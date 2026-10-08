package com.auralink.critic.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.CriticProperties;
import com.auralink.critic.provider.CriticEvaluationProvider;
import com.auralink.critic.provider.CriticProviderException;
import com.auralink.entity.MediaAsset;
import com.auralink.entity.Painting;
import com.auralink.entity.PaintingCritique;
import com.auralink.entity.User;
import com.auralink.service.CurrentUserService;
import com.auralink.service.media.MediaAssetStorageService;
import com.auralink.service.painting.PaintingQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class PaintingCritiqueService {
   private static final Set<String> PROFILES = Set.of("authoritative_four", "requested_four");
   private final CurrentUserService users;
   private final PaintingQueryService paintings;
   private final CriticProperties properties;
   private final PaintingCritiqueTaskStore store;
   private final CriticEvaluationProvider provider;
   private final CriticResponseValidator validator;
   private final MediaAssetStorageService storage;
   private final ThreadPoolTaskExecutor executor;

   public PaintingCritiqueService(
      CurrentUserService users,
      PaintingQueryService paintings,
      CriticProperties properties,
      PaintingCritiqueTaskStore store,
      CriticEvaluationProvider provider,
      CriticResponseValidator validator,
      MediaAssetStorageService storage,
      @Qualifier("criticTaskExecutor") ThreadPoolTaskExecutor executor
   ) {
      this.users = users;
      this.paintings = paintings;
      this.properties = properties;
      this.store = store;
      this.provider = provider;
      this.validator = validator;
      this.storage = storage;
      this.executor = executor;
   }

   public boolean submissionAvailable() {
      return this.properties.isConfigured();
   }

   public PaintingCritiqueService.Submission submit(String paintingId, String requestedProfile) {
      this.requireEnabled();
      User owner = this.users.requireCurrentUser();
      Painting painting = this.paintings.requireActivePainting(paintingId);
      MediaAsset image = this.requireImage(painting);
      String profile = this.normalizeProfile(requestedProfile);
      String titleSnapshot = this.normalizeTitle(painting.getTitle());
      String fingerprint = this.fingerprint(owner, painting, image, titleSnapshot, profile);

      PaintingCritiqueTaskStore.Admission admission;
      try {
         admission = this.store
            .admit(
               owner,
               painting,
               image,
               titleSnapshot,
               profile,
               fingerprint,
               this.properties.getEvaluatorVersion(),
               this.properties.getModelIdentity(),
               this.properties.getPromptSchemaVersion()
            );
      } catch (DataIntegrityViolationException race) {
         PaintingCritique existing = this.store.existingEquivalent(owner.getId(), painting.getId(), fingerprint).orElseThrow(() -> race);
         admission = new PaintingCritiqueTaskStore.Admission(existing, false, "SUCCEEDED".equals(existing.getStatus()));
      }

      PaintingCritiqueTaskStore.Admission admitted = admission;
      if (admitted.dispatchRequired()) {
         try {
            this.executor.execute(() -> this.execute(admitted.task().getPublicId()));
         } catch (TaskRejectedException rejected) {
            this.store
               .claim(admitted.task().getPublicId(), this.properties.getOverallDeadline())
               .ifPresent(task -> this.store.fail(task.getPublicId(), "QUEUE_FULL", "智能评析任务队列暂时已满"));
            throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CRITIC_QUEUE_FULL, "智能评析任务队列暂时已满");
         }
      }

      return new PaintingCritiqueService.Submission(admitted.task(), admitted.reusedSuccess());
   }

   public PaintingCritique current(String paintingId) {
      User owner = this.users.requireCurrentUser();
      Painting painting = this.paintings.requireActivePainting(paintingId);
      return this.store
         .current(owner.getId(), painting.getId())
         .orElseThrow(() -> new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.CRITIC_TASK_NOT_FOUND, "该画作尚无可用智能评析"));
   }

   public PaintingCritique get(String taskId) {
      User owner = this.users.requireCurrentUser();
      return this.store
         .owned(taskId, owner.getId())
         .orElseThrow(() -> new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.CRITIC_TASK_NOT_FOUND, "智能评析任务不存在或无权访问"));
   }

   private void execute(String taskId) {
      PaintingCritique task = this.store.claim(taskId, this.properties.getOverallDeadline()).orElse(null);
      if (task != null) {
         try {
            FileSystemResource image = this.storage.resolve(task.getImageAsset()).resource();
            JsonNode response = this.provider.evaluate(new CriticEvaluationProvider.Request(task.getProfile(), task.getTitleSnapshot(), image));
            this.store.succeed(taskId, this.validator.canonicalize(response, task.getProfile()));
         } catch (CriticProviderException failure) {
            this.store.fail(taskId, switch (failure.failure()) {
               case TIMEOUT -> "PROVIDER_TIMEOUT";
               case INVALID_RESPONSE -> "INVALID_RESPONSE";
               case UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
            }, this.publicFailureMessage(failure));
         } catch (RuntimeException failure) {
            this.store.fail(taskId, "INTERNAL_FAILURE", "智能评析结果未能保存，请稍后重新发起");
         }
      }
   }

   private void requireEnabled() {
      if (!this.properties.isConfigured()) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CRITIC_DISABLED, "智能评析服务当前未启用或未完成配置");
      }
   }

   private MediaAsset requireImage(Painting painting) {
      MediaAsset image = painting.getImageAsset();
      if (painting.isImageAvailable()
         && image != null
         && "ACTIVE".equals(image.getStatus())
         && "PUBLIC".equals(image.getVisibility())
         && image.getSha256() != null
         && !image.getSha256().isBlank()) {
         return image;
      } else {
         throw new ApiV1Exception(HttpStatus.CONFLICT, ApiErrorCode.CRITIC_INVALID_RESPONSE, "该画作当前没有可供评析的有效图像");
      }
   }

   private String normalizeProfile(String value) {
      String profile = value != null && !value.isBlank() ? value.trim() : "authoritative_four";
      if (!PROFILES.contains(profile)) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "不支持的智能评析方案");
      } else {
         return profile;
      }
   }

   private String normalizeTitle(String value) {
      return value != null && !value.isBlank() ? value.trim() : null;
   }

   private String fingerprint(User owner, Painting painting, MediaAsset image, String titleSnapshot, String profile) {
      try {
         String title = titleSnapshot == null ? "<null>" : titleSnapshot;
         String value = owner.getId()
            + "|"
            + painting.getPublicId()
            + "|"
            + image.getSha256()
            + "|"
            + title
            + "|"
            + profile
            + "|"
            + this.properties.getEvaluatorVersion()
            + "|"
            + this.properties.getModelIdentity()
            + "|"
            + this.properties.getPromptSchemaVersion();
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
         throw new IllegalStateException("Critic fingerprint could not be calculated", exception);
      }
   }

   private String publicFailureMessage(CriticProviderException failure) {
      return switch (failure.failure()) {
         case TIMEOUT -> "智能评析超时，未自动重复请求";
         case INVALID_RESPONSE -> "智能评析服务返回了无效结果";
         case UNAVAILABLE -> "智能评析服务暂时不可用";
      };
   }

   public record Submission(PaintingCritique task, boolean reusedSuccess) {
   }
}

package com.auralink.catalogcritic;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.catalogread.CatalogMediaService;
import com.auralink.catalogread.CatalogReadStore;
import com.auralink.config.properties.CriticProperties;
import com.auralink.critic.provider.CriticEvaluationProvider;
import com.auralink.critic.provider.CriticProviderException;
import com.auralink.critic.service.CriticResponseValidator;
import com.auralink.entity.User;
import com.auralink.service.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class PostgresPaintingCritiqueService {
   private static final Set<String> PROFILES = Set.of("authoritative_four", "requested_four");
   private final CurrentUserService users;
   private final CatalogReadStore catalog;
   private final CatalogMediaService catalogMedia;
   private final CriticProperties properties;
   private final CatalogCriticProperties catalogProperties;
   private final PostgresCatalogCriticStore store;
   private final CriticEvaluationProvider provider;
   private final CriticResponseValidator validator;
   private final ThreadPoolTaskExecutor executor;
   private final ObjectMapper mapper;

   public PostgresPaintingCritiqueService(
      CurrentUserService users,
      CatalogReadStore catalog,
      CatalogMediaService catalogMedia,
      CriticProperties properties,
      CatalogCriticProperties catalogProperties,
      PostgresCatalogCriticStore store,
      CriticEvaluationProvider provider,
      CriticResponseValidator validator,
      ThreadPoolTaskExecutor executor,
      ObjectMapper mapper
   ) {
      this.users = users;
      this.catalog = catalog;
      this.catalogMedia = catalogMedia;
      this.properties = properties;
      this.catalogProperties = catalogProperties;
      this.store = store;
      this.provider = provider;
      this.validator = validator;
      this.executor = executor;
      this.mapper = mapper;
   }

   public boolean submissionAvailable() {
      return this.properties.isConfigured();
   }

   public PostgresPaintingCritiqueService.Submission submit(String paintingId, String requestedProfile) {
      this.requireEnabled();
      User owner = this.users.requireCurrentUser();
      CatalogReadStore.CatalogPainting painting = this.requirePainting(paintingId);
      String profile = this.normalizeProfile(requestedProfile);
      String title = normalizeTitle(painting.title());
      String fingerprint = this.fingerprint(owner.getId(), painting.publicId(), painting.image().contentSha256(), title, profile);

      PostgresCatalogCriticStore.Admission admission;
      try {
         admission = this.store
            .admit(
               owner.getId(),
               painting.publicId(),
               painting.image().publicId(),
               painting.image().contentSha256(),
               title,
               profile,
               fingerprint,
               this.properties.getEvaluatorVersion(),
               this.properties.getModelIdentity(),
               this.properties.getPromptSchemaVersion(),
               this.snapshot(painting)
            );
      } catch (CatalogCriticAdmissionRaceException race) {
         CatalogCriticTask existing = this.store.existingEquivalent(owner.getId(), painting.publicId(), fingerprint).orElseThrow(() -> race);
         admission = new PostgresCatalogCriticStore.Admission(existing, false, "SUCCEEDED".equals(existing.status()));
      }

      PostgresCatalogCriticStore.Admission admitted = admission;
      if (admitted.dispatchRequired()) {
         try {
            this.executor.execute(() -> this.execute(admitted.task().publicId()));
         } catch (TaskRejectedException exception) {
            this.store
               .claim(admitted.task().publicId(), this.properties.getOverallDeadline())
               .ifPresent(task -> this.store.fail(task.publicId(), "QUEUE_FULL", "智能评析任务队列暂时已满"));
            throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CRITIC_QUEUE_FULL, "智能评析任务队列暂时已满");
         }
      }

      return new PostgresPaintingCritiqueService.Submission(admitted.task(), admitted.reusedSuccess());
   }

   public CatalogCriticTask current(String paintingId) {
      User owner = this.users.requireCurrentUser();
      CatalogReadStore.CatalogPainting painting = this.requirePainting(paintingId);
      return this.store.current(owner.getId(), painting.publicId()).orElseThrow(this::notFound);
   }

   public CatalogCriticTask get(String taskId) {
      User owner = this.users.requireCurrentUser();
      return this.store.owned(taskId, owner.getId()).orElseThrow(this::notFound);
   }

   public int terminalizeExpiredRunning() {
      return this.store.terminalizeExpiredRunning();
   }

   private void execute(String taskId) {
      CatalogCriticTask task = this.store.claim(taskId, this.properties.getOverallDeadline()).orElse(null);
      if (task != null) {
         try {
            CatalogMediaService.CatalogMediaContent image = this.catalogMedia
               .find(task.imageAssetId())
               .orElseThrow(() -> new CriticProviderException(CriticProviderException.Failure.INVALID_RESPONSE, "Critic source image is unavailable"));
            JsonNode response = this.provider.evaluate(new CriticEvaluationProvider.Request(task.profile(), task.titleSnapshot(), image.resource()));
            String canonical = this.validator.canonicalize(response, task.profile());
            if (this.catalogProperties.isFailAfterProviderResponse()) {
               throw new CriticCompletionUncertainException();
            }

            this.store.succeed(task.publicId(), canonical);
         } catch (CriticCompletionUncertainException var6) {
         } catch (CriticProviderException failure) {
            this.store.fail(task.publicId(), switch (failure.failure()) {
               case TIMEOUT -> "PROVIDER_TIMEOUT";
               case INVALID_RESPONSE -> "INVALID_RESPONSE";
               case UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
            }, this.publicFailureMessage(failure));
         } catch (RuntimeException failure) {
            this.store.fail(task.publicId(), "INTERNAL_FAILURE", "智能评析结果未能保存，请稍后重新发起");
         }
      }
   }

   private CatalogReadStore.CatalogPainting requirePainting(String paintingId) {
      String canonical = this.canonicalPaintingId(paintingId);
      CatalogReadStore.CatalogPainting painting = this.catalog.findPainting(canonical).orElseThrow(this::paintingNotFound);
      if ("ACTIVE".equals(painting.catalogStatus())
         && painting.imageReady()
         && painting.image() != null
         && painting.image().contentSha256() != null
         && !painting.image().contentSha256().isBlank()) {
         return painting;
      } else {
         throw new ApiV1Exception(HttpStatus.CONFLICT, ApiErrorCode.CRITIC_INVALID_RESPONSE, "该画作当前没有可供评析的有效图像");
      }
   }

   private String snapshot(CatalogReadStore.CatalogPainting painting) {
      ObjectNode root = this.mapper.createObjectNode();
      root.put("paintingId", painting.publicId());
      root.put("sourceRecordKey", painting.sourceRecordKey());
      root.put("title", painting.title());
      root.put("authorName", painting.authorName());
      root.put("category", painting.category());
      root.put("imageAssetId", painting.image().publicId());
      root.put("imageSha256", painting.image().contentSha256());
      ArrayNode annotations = root.putArray("publicAnnotations");

      for (CatalogReadStore.CatalogAnnotation annotation : painting.annotations()) {
         if ("public".equalsIgnoreCase(annotation.visibility())) {
            ObjectNode value = annotations.addObject();
            value.put("fieldKey", annotation.fieldKey());
            value.put("rawValue", annotation.rawValue());
            value.put("normalizedValue", annotation.normalizedValue());
            value.put("valueStatus", annotation.valueStatus());
            value.put("assertionStatus", annotation.assertionStatus());
         }
      }

      try {
         return this.mapper.writeValueAsString(root);
      } catch (Exception exception) {
         throw new IllegalStateException("Critic input snapshot cannot be serialized", exception);
      }
   }

   private void requireEnabled() {
      if (!this.properties.isConfigured()) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CRITIC_DISABLED, "智能评析服务当前未启用或未完成配置");
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

   private static String normalizeTitle(String value) {
      return value != null && !value.isBlank() ? value.trim() : null;
   }

   private String fingerprint(long userId, String paintingId, String imageSha256, String title, String profile) {
      try {
         String value = userId
            + "|"
            + paintingId
            + "|"
            + imageSha256
            + "|"
            + (title == null ? "<null>" : title)
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

   private String canonicalPaintingId(String value) {
      try {
         String canonical = UUID.fromString(value).toString();
         if (!canonical.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("non-canonical UUID");
         } else {
            return canonical;
         }
      } catch (RuntimeException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PAINTING_ID, "画作标识格式无效");
      }
   }

   private ApiV1Exception paintingNotFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.PAINTING_NOT_FOUND, "画作不存在或当前不可用");
   }

   private ApiV1Exception notFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.CRITIC_TASK_NOT_FOUND, "智能评析任务不存在或无权访问");
   }

   private String publicFailureMessage(CriticProviderException failure) {
      return switch (failure.failure()) {
         case TIMEOUT -> "智能评析超时，未自动重复请求";
         case INVALID_RESPONSE -> "智能评析服务返回了无效结果";
         case UNAVAILABLE -> "智能评析服务暂时不可用";
      };
   }

   public record Submission(CatalogCriticTask task, boolean reusedSuccess) {
   }
}

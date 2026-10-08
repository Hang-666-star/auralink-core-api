package com.auralink.guide.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.GuideProperties;
import com.auralink.entity.Painting;
import com.auralink.entity.PaintingGuide;
import com.auralink.guide.context.PaintingGuideContext;
import com.auralink.guide.context.PaintingGuideContextBuilder;
import com.auralink.guide.hash.GuideSourceHasher;
import com.auralink.guide.knowledge.KnowledgeContextBuilder;
import com.auralink.guide.knowledge.KnowledgeSelection;
import com.auralink.guide.knowledge.StaticKnowledgeLoader;
import com.auralink.guide.model.GuideResult;
import com.auralink.guide.model.GuideResultCodec;
import com.auralink.guide.model.GuideResultValidationException;
import com.auralink.guide.provider.GuideGenerationResult;
import com.auralink.guide.provider.GuideProvider;
import com.auralink.guide.provider.GuideProviderException;
import com.auralink.service.painting.PaintingQueryService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaintingGuideService {
   private final PaintingQueryService paintingQueryService;
   private final PaintingGuideContextBuilder contextBuilder;
   private final KnowledgeContextBuilder knowledgeContextBuilder;
   private final GuideSourceHasher sourceHasher;
   private final GuideResultCodec resultCodec;
   private final GuideProvider guideProvider;
   private final PaintingGuideCacheStore cacheStore;
   private final PaintingGuideLockRegistry lockRegistry;
   private final PaintingGuideGenerationGuard generationGuard;
   private final GuideProperties properties;

   public PaintingGuideOutcome getCurrentGuide(String paintingId) {
      PaintingGuideService.PreparedGuide prepared = this.prepare(paintingId);
      return this.current(prepared, GuideCacheStatus.HIT).orElseThrow(PaintingGuideService::notAvailable);
   }

   public PaintingGuideOutcome ensureGuide(String paintingId, String requester) {
      PaintingGuideService.PreparedGuide initial = this.prepare(paintingId);
      Optional<PaintingGuideOutcome> cached = this.current(initial, GuideCacheStatus.HIT);
      if (cached.isPresent()) {
         return cached.orElseThrow();
      }

      this.requireGenerationEnabled();
      return this.lockRegistry.withPaintingLock(initial.painting().getPublicId(), () -> {
         PaintingGuideService.PreparedGuide prepared = this.prepare(initial.painting().getPublicId());
         Optional<PaintingGuideOutcome> secondRead = this.current(prepared, GuideCacheStatus.HIT);
         if (secondRead.isPresent()) {
            return secondRead.orElseThrow();
         }

         this.requireGenerationEnabled();
         return this.generationGuard.withPaidGeneration(requester, () -> this.generateAndStore(prepared));
      });
   }

   public void requirePaintingForReservedAudio(String paintingId) {
      this.paintingQueryService.requireActivePainting(paintingId);
      throw new ApiV1Exception(HttpStatus.NOT_IMPLEMENTED, ApiErrorCode.GUIDE_TTS_NOT_ENABLED, "画作导览语音功能尚未启用");
   }

   private PaintingGuideService.PreparedGuide prepare(String paintingId) {
      Painting painting = this.paintingQueryService.requireActivePainting(paintingId);

      try {
         PaintingGuideContext base = this.contextBuilder.build(painting);
         KnowledgeSelection selection = this.knowledgeContextBuilder.build(base);
         PaintingGuideContext context = base.withKnowledge(selection.items());
         String sourceHash = this.sourceHasher.hash(this.properties.getSchemaVersion(), context, selection);
         return new PaintingGuideService.PreparedGuide(painting, context, selection, sourceHash);
      } catch (StaticKnowledgeLoader.KnowledgeLoadingException | IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_CONTEXT_INVALID, "画作导览上下文当前不可用");
      }
   }

   private Optional<PaintingGuideOutcome> current(PaintingGuideService.PreparedGuide prepared, GuideCacheStatus cacheStatus) {
      return this.cacheStore.findByPaintingId(prepared.painting().getId()).flatMap(row -> this.decodeCurrent(row, prepared, cacheStatus));
   }

   private Optional<PaintingGuideOutcome> decodeCurrent(PaintingGuide row, PaintingGuideService.PreparedGuide prepared, GuideCacheStatus cacheStatus) {
      if ("SUCCESS".equals(row.getStatus()) && prepared.sourceHash().equals(row.getSourceHash()) && row.getGeneratedAt() != null && row.getUpdatedAt() != null) {
         try {
            GuideResult result = this.resultCodec.decode(row.getResultJson(), this.properties.getSchemaVersion(), prepared.context().knowledge());
            return Optional.of(new PaintingGuideOutcome(prepared.painting().getPublicId(), result, cacheStatus, row.getGeneratedAt(), row.getUpdatedAt()));
         } catch (GuideResultValidationException exception) {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private PaintingGuideOutcome generateAndStore(PaintingGuideService.PreparedGuide prepared) {
      String requestId = UUID.randomUUID().toString();

      GuideGenerationResult generated;
      try {
         generated = this.guideProvider.generate(requestId, prepared.context());
      } catch (GuideProviderException exception) {
         throw this.publicProviderFailure(exception);
      }

      if (generated != null && requestId.equals(generated.requestId())) {
         String canonicalJson;
         try {
            canonicalJson = this.resultCodec.encodeCanonical(generated.result(), this.properties.getSchemaVersion(), prepared.context().knowledge());
         } catch (GuideResultValidationException exception) {
            throw invalidProviderResponse();
         }

         try {
            this.cacheStore.saveSuccess(prepared.painting(), prepared.sourceHash(), canonicalJson);
            return this.current(prepared, GuideCacheStatus.GENERATED).orElseThrow(PaintingGuideService::invalidProviderResponse);
         } catch (DataIntegrityViolationException exception) {
            return this.current(prepared, GuideCacheStatus.HIT).orElseThrow(PaintingGuideService::invalidProviderResponse);
         }
      } else {
         throw invalidProviderResponse();
      }
   }

   private void requireGenerationEnabled() {
      if (!this.properties.isEnabled() || this.properties.getInternalToken() == null || this.properties.getInternalToken().isBlank()) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_DISABLED, "画作导览生成功能当前未启用");
      }
   }

   private ApiV1Exception publicProviderFailure(GuideProviderException exception) {
      return switch (exception.getFailure()) {
         case CONFIGURATION -> new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_DISABLED, "画作导览生成功能当前未配置");
         case TIMEOUT -> new ApiV1Exception(HttpStatus.GATEWAY_TIMEOUT, ApiErrorCode.GUIDE_PROVIDER_TIMEOUT, "画作导览生成超时");
         case UNAVAILABLE -> new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_PROVIDER_UNAVAILABLE, "画作导览生成服务暂时不可用");
         case REJECTED -> new ApiV1Exception(HttpStatus.BAD_GATEWAY, ApiErrorCode.GUIDE_PROVIDER_REJECTED, "画作导览生成请求未被上游接受");
         case INVALID_RESPONSE -> invalidProviderResponse();
      };
   }

   private static ApiV1Exception notAvailable() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.GUIDE_NOT_AVAILABLE, "该画作尚无可用的标准导览");
   }

   private static ApiV1Exception invalidProviderResponse() {
      return new ApiV1Exception(HttpStatus.BAD_GATEWAY, ApiErrorCode.GUIDE_INVALID_RESPONSE, "画作导览服务返回了无效结果");
   }

   public PaintingGuideService(
      final PaintingQueryService paintingQueryService,
      final PaintingGuideContextBuilder contextBuilder,
      final KnowledgeContextBuilder knowledgeContextBuilder,
      final GuideSourceHasher sourceHasher,
      final GuideResultCodec resultCodec,
      final GuideProvider guideProvider,
      final PaintingGuideCacheStore cacheStore,
      final PaintingGuideLockRegistry lockRegistry,
      final PaintingGuideGenerationGuard generationGuard,
      final GuideProperties properties
   ) {
      this.paintingQueryService = paintingQueryService;
      this.contextBuilder = contextBuilder;
      this.knowledgeContextBuilder = knowledgeContextBuilder;
      this.sourceHasher = sourceHasher;
      this.resultCodec = resultCodec;
      this.guideProvider = guideProvider;
      this.cacheStore = cacheStore;
      this.lockRegistry = lockRegistry;
      this.generationGuard = generationGuard;
      this.properties = properties;
   }

   private record PreparedGuide(Painting painting, PaintingGuideContext context, KnowledgeSelection selection, String sourceHash) {
   }
}

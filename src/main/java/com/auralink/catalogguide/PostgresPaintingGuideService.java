package com.auralink.catalogguide;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.catalogread.CatalogReadStore;
import com.auralink.config.properties.GuideProperties;
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
import com.auralink.guide.service.GuideCacheStatus;
import com.auralink.guide.service.PaintingGuideGenerationGuard;
import com.auralink.guide.service.PaintingGuideLockRegistry;
import com.auralink.guide.service.PaintingGuideOutcome;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

public class PostgresPaintingGuideService {
   private final CatalogReadStore catalogReadStore;
   private final PaintingGuideContextBuilder contextBuilder;
   private final KnowledgeContextBuilder knowledgeContextBuilder;
   private final GuideSourceHasher sourceHasher;
   private final GuideResultCodec resultCodec;
   private final GuideProvider guideProvider;
   private final PostgresCatalogGuideStore store;
   private final PaintingGuideLockRegistry lockRegistry;
   private final PaintingGuideGenerationGuard generationGuard;
   private final GuideProperties properties;

   public PostgresPaintingGuideService(
      CatalogReadStore catalogReadStore,
      PaintingGuideContextBuilder contextBuilder,
      KnowledgeContextBuilder knowledgeContextBuilder,
      GuideSourceHasher sourceHasher,
      GuideResultCodec resultCodec,
      GuideProvider guideProvider,
      PostgresCatalogGuideStore store,
      PaintingGuideLockRegistry lockRegistry,
      PaintingGuideGenerationGuard generationGuard,
      GuideProperties properties
   ) {
      this.catalogReadStore = catalogReadStore;
      this.contextBuilder = contextBuilder;
      this.knowledgeContextBuilder = knowledgeContextBuilder;
      this.sourceHasher = sourceHasher;
      this.resultCodec = resultCodec;
      this.guideProvider = guideProvider;
      this.store = store;
      this.lockRegistry = lockRegistry;
      this.generationGuard = generationGuard;
      this.properties = properties;
   }

   public PaintingGuideOutcome getCurrentGuide(String paintingId) {
      PostgresPaintingGuideService.PreparedGuide prepared = this.prepare(paintingId);
      return this.current(prepared, GuideCacheStatus.HIT).orElseThrow(PostgresPaintingGuideService::notAvailable);
   }

   public HistoricalPaintingGuideOutcome getHistoricalGuide(String paintingId) {
      String canonicalId = this.requireActivePaintingId(paintingId);

      try {
         return this.store
            .findLatestLegacyHistoricalByPaintingId(canonicalId)
            .flatMap(this::decodeHistorical)
            .orElseThrow(PostgresPaintingGuideService::historicalNotAvailable);
      } catch (DataAccessException exception) {
         throw catalogUnavailable(exception);
      }
   }

   public PaintingGuideOutcome ensureGuide(String paintingId, String requester) {
      PostgresPaintingGuideService.PreparedGuide initial = this.prepare(paintingId);
      Optional<PaintingGuideOutcome> cached = this.current(initial, GuideCacheStatus.HIT);
      if (cached.isPresent()) {
         return cached.orElseThrow();
      }

      this.requireGenerationEnabled();
      return this.lockRegistry.withPaintingLock(initial.painting().publicId(), () -> {
         PostgresPaintingGuideService.PreparedGuide prepared = this.prepare(initial.painting().publicId());
         Optional<PaintingGuideOutcome> second = this.current(prepared, GuideCacheStatus.HIT);
         if (second.isPresent()) {
            return second.orElseThrow();
         }

         this.requireGenerationEnabled();
         return this.generationGuard.withPaidGeneration(requester, () -> this.generateAndStore(prepared));
      });
   }

   public void requirePaintingForReservedAudio(String paintingId) {
      this.prepare(paintingId);
      throw new ApiV1Exception(HttpStatus.NOT_IMPLEMENTED, ApiErrorCode.GUIDE_TTS_NOT_ENABLED, "画作导览语音功能尚未启用");
   }

   private PostgresPaintingGuideService.PreparedGuide prepare(String paintingId) {
      try {
         String canonicalId = this.requireActivePaintingId(paintingId);
         CatalogReadStore.CatalogPainting painting = this.catalogReadStore
            .findPainting(canonicalId)
            .orElseThrow(PostgresPaintingGuideService::paintingNotFound);
         PaintingGuideContext base = this.contextBuilder.build(painting);
         KnowledgeSelection selection = this.knowledgeContextBuilder.build(base);
         PaintingGuideContext context = base.withKnowledge(selection.items());
         return new PostgresPaintingGuideService.PreparedGuide(
            painting, context, selection, this.sourceHasher.hash(this.properties.getSchemaVersion(), context, selection)
         );
      } catch (IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PAINTING_ID, "画作 ID 格式无效");
      } catch (StaticKnowledgeLoader.KnowledgeLoadingException exception) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_CONTEXT_INVALID, "画作导览上下文当前不可用");
      } catch (DataAccessException exception) {
         throw catalogUnavailable(exception);
      }
   }

   private String requireActivePaintingId(String paintingId) {
      try {
         String canonicalId = UUID.fromString(paintingId).toString();
         this.catalogReadStore
            .findPainting(canonicalId)
            .filter(candidate -> "ACTIVE".equals(candidate.catalogStatus()))
            .orElseThrow(PostgresPaintingGuideService::paintingNotFound);
         return canonicalId;
      } catch (IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PAINTING_ID, "画作 ID 格式无效");
      } catch (DataAccessException exception) {
         throw catalogUnavailable(exception);
      }
   }

   private Optional<PaintingGuideOutcome> current(PostgresPaintingGuideService.PreparedGuide prepared, GuideCacheStatus status) {
      try {
         return this.store.findByPaintingId(prepared.painting().publicId()).flatMap(row -> this.decodeCurrent(row, prepared, status));
      } catch (DataAccessException exception) {
         throw catalogUnavailable(exception);
      }
   }

   private Optional<PaintingGuideOutcome> decodeCurrent(
      PostgresCatalogGuideStore.GuideCacheRow row, PostgresPaintingGuideService.PreparedGuide prepared, GuideCacheStatus cacheStatus
   ) {
      if ("SUCCESS".equals(row.status()) && prepared.sourceHash().equals(row.sourceHash()) && row.generatedAt() != null && row.updatedAt() != null) {
         try {
            GuideResult result = this.resultCodec.decode(row.resultJson(), this.properties.getSchemaVersion(), prepared.context().knowledge());
            return Optional.of(new PaintingGuideOutcome(prepared.painting().publicId(), result, cacheStatus, row.generatedAt(), row.updatedAt()));
         } catch (GuideResultValidationException exception) {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private Optional<HistoricalPaintingGuideOutcome> decodeHistorical(PostgresCatalogGuideStore.HistoricalGuideRow row) {
      if (row.generatedAt() != null && row.updatedAt() != null && row.sourceHash() != null && row.sourceHash().matches("[0-9a-f]{64}")) {
         try {
            GuideResult result = this.resultCodec.decodeHistorical(row.resultJson(), this.properties.getSchemaVersion());
            return Optional.of(new HistoricalPaintingGuideOutcome(row.paintingId(), result, row.generatedAt(), row.updatedAt()));
         } catch (GuideResultValidationException exception) {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   private PaintingGuideOutcome generateAndStore(PostgresPaintingGuideService.PreparedGuide prepared) {
      UUID requestId = UUID.randomUUID();

      PostgresCatalogGuideStore.GenerationAdmission admission;
      try {
         admission = this.store.admit(prepared.painting().publicId(), prepared.sourceHash(), requestId);
      } catch (DataAccessException exception) {
         throw catalogUnavailable(exception);
      }

      if (admission.state() == PostgresCatalogGuideStore.GenerationAdmission.State.UNCERTAIN) {
         throw generationUncertain();
      }

      if (admission.state() == PostgresCatalogGuideStore.GenerationAdmission.State.IN_PROGRESS) {
         throw new ApiV1Exception(HttpStatus.CONFLICT, ApiErrorCode.GUIDE_PROVIDER_UNAVAILABLE, "同一画作导览正在生成，请稍后读取结果");
      }

      GuideGenerationResult generated;
      try {
         generated = this.guideProvider.generate(requestId.toString(), prepared.context());
      } catch (GuideProviderException exception) {
         this.markProviderFailure(admission.attemptId(), exception.getFailure().name());
         throw this.publicProviderFailure(exception);
      }

      if (generated != null && requestId.toString().equals(generated.requestId())) {
         String canonicalJson;
         try {
            canonicalJson = this.resultCodec.encodeCanonical(generated.result(), this.properties.getSchemaVersion(), prepared.context().knowledge());
         } catch (GuideResultValidationException exception) {
            this.markProviderFailure(admission.attemptId(), "INVALID_RESPONSE");
            throw invalidProviderResponse();
         }

         if (Boolean.getBoolean("auralink.catalog-guide.fail-after-provider-response")) {
            this.markUncertain(admission.attemptId());
            throw generationUncertain();
         }

         try {
            this.store.saveSuccess(admission.attemptId(), prepared.painting(), prepared.sourceHash(), canonicalJson, prepared.context(), prepared.selection());
            return this.current(prepared, GuideCacheStatus.GENERATED).orElseThrow(PostgresPaintingGuideService::invalidProviderResponse);
         } catch (DataAccessException | IllegalStateException exception) {
            this.markUncertain(admission.attemptId());
            throw catalogUnavailable(exception);
         }
      } else {
         this.markProviderFailure(admission.attemptId(), "INVALID_RESPONSE");
         throw invalidProviderResponse();
      }
   }

   private void markProviderFailure(UUID attemptId, String code) {
      try {
         this.store.markProviderFailure(attemptId, code);
      } catch (DataAccessException var4) {
      }
   }

   private void markUncertain(UUID attemptId) {
      try {
         this.store.markPostResponseUncertain(attemptId);
      } catch (DataAccessException var3) {
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

   private static ApiV1Exception paintingNotFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.PAINTING_NOT_FOUND, "画作不存在或当前不可用");
   }

   private static ApiV1Exception notAvailable() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.GUIDE_NOT_AVAILABLE, "该画作尚无可用的标准导览");
   }

   private static ApiV1Exception historicalNotAvailable() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.GUIDE_HISTORICAL_NOT_AVAILABLE, "该画作没有可安全呈现的历史导览");
   }

   private static ApiV1Exception invalidProviderResponse() {
      return new ApiV1Exception(HttpStatus.BAD_GATEWAY, ApiErrorCode.GUIDE_INVALID_RESPONSE, "画作导览服务返回了无效结果");
   }

   private static ApiV1Exception generationUncertain() {
      return new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_GENERATION_UNCERTAIN, "导览请求结果尚未确认，系统不会自动重复调用提供方");
   }

   private static ApiV1Exception catalogUnavailable(Exception cause) {
      return new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_READ_UNAVAILABLE, "目录存储暂不可用，未使用 SQLite 回退");
   }

   private record PreparedGuide(CatalogReadStore.CatalogPainting painting, PaintingGuideContext context, KnowledgeSelection selection, String sourceHash) {
   }
}

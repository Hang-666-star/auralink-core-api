package com.auralink.catalogguide;

import com.auralink.catalogread.CatalogReadStore;
import com.auralink.guide.context.PaintingGuideContext;
import com.auralink.guide.knowledge.KnowledgeSelection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PostgresCatalogGuideStore {
   static final String SUCCESS_STATUS = "SUCCESS";
   static final String DISPATCHING = "DISPATCHING";
   static final String POST_RESPONSE_UNCERTAIN = "POST_RESPONSE_UNCERTAIN";
   private static final ZoneId R24_TIMESTAMP_ZONE = ZoneId.of("Asia/Shanghai");
   private final NamedParameterJdbcTemplate jdbc;
   private final ObjectMapper objectMapper;

   public PostgresCatalogGuideStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
      this.jdbc = jdbc;
      this.objectMapper = objectMapper;
   }

   @Transactional(transactionManager = "catalogGuideTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<PostgresCatalogGuideStore.GuideCacheRow> findByPaintingId(String paintingId) {
      return this.jdbc
         .query(
            "SELECT public_id::text, painting_id::text, source_hash, status, legacy_original_status,\n       result_json::text, generated_at, updated_at,\n       input_batch_id::text, input_revision, input_snapshot::text\n  FROM catalog_painting_guides\n WHERE painting_id = :paintingId::uuid\n",
            new MapSqlParameterSource("paintingId", paintingId),
            this.guideRowMapper()
         )
         .stream()
         .findFirst();
   }

   @Transactional(transactionManager = "catalogGuideTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<PostgresCatalogGuideStore.HistoricalGuideRow> findLatestLegacyHistoricalByPaintingId(String paintingId) {
      return this.jdbc
         .query(
            "SELECT r.public_id::text, g.painting_id::text, r.source_hash, r.result_json::text,\n       r.input_snapshot::text, r.generated_at, g.updated_at\n  FROM catalog_painting_guide_result_revisions r\n  JOIN catalog_painting_guides g ON g.public_id = r.guide_id\n WHERE g.painting_id = :paintingId::uuid\n   AND jsonb_exists(r.input_snapshot, 'legacyGuideId')\n ORDER BY r.generated_at DESC, r.public_id DESC\n LIMIT 1\n",
            new MapSqlParameterSource("paintingId", paintingId),
            (rs, row) -> new PostgresCatalogGuideStore.HistoricalGuideRow(
               rs.getString("public_id"),
               rs.getString("painting_id"),
               rs.getString("source_hash"),
               rs.getString("result_json"),
               rs.getString("input_snapshot"),
               this.localTimestamp(rs.getObject("generated_at", OffsetDateTime.class)),
               this.localTimestamp(rs.getObject("updated_at", OffsetDateTime.class))
            )
         )
         .stream()
         .findFirst();
   }

   @Transactional(transactionManager = "catalogGuideTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public PostgresCatalogGuideStore.GenerationAdmission admit(String paintingId, String sourceHash, UUID requestId) {
      MapSqlParameterSource parameters = new MapSqlParameterSource()
         .addValue("paintingId", paintingId)
         .addValue("sourceHash", sourceHash)
         .addValue("attemptId", UUID.randomUUID())
         .addValue("requestId", requestId);

      try {
         int inserted = this.jdbc
            .update(
               "INSERT INTO catalog_painting_guide_generation_attempts\n    (public_id, painting_id, source_hash, request_id, status)\nVALUES (:attemptId, :paintingId::uuid, :sourceHash, :requestId::uuid, 'DISPATCHING')\nON CONFLICT DO NOTHING\n",
               parameters
            );
         return inserted == 1
            ? PostgresCatalogGuideStore.GenerationAdmission.dispatch((UUID)parameters.getValue("attemptId"))
            : this.jdbc
               .query(
                  "SELECT public_id::text, status\n  FROM catalog_painting_guide_generation_attempts\n WHERE painting_id = :paintingId::uuid AND source_hash = :sourceHash\n   AND status IN ('DISPATCHING', 'POST_RESPONSE_UNCERTAIN')\n ORDER BY created_at DESC\n",
                  parameters,
                  (rs, row) -> new PostgresCatalogGuideStore.ExistingAttempt(UUID.fromString(rs.getString("public_id")), rs.getString("status"))
               )
               .stream()
               .findFirst()
               .map(
                  existing -> "POST_RESPONSE_UNCERTAIN".equals(existing.status())
                     ? PostgresCatalogGuideStore.GenerationAdmission.uncertain(existing.publicId())
                     : PostgresCatalogGuideStore.GenerationAdmission.inProgress(existing.publicId())
               )
               .orElseThrow(() -> new IllegalStateException("Guide admission conflict could not be read"));
      } catch (DataAccessException exception) {
         throw exception;
      }
   }

   @Transactional(transactionManager = "catalogGuideTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public void markProviderFailure(UUID attemptId, String failureCode) {
      this.jdbc
         .update(
            "UPDATE catalog_painting_guide_generation_attempts\n   SET status = 'FAILED_BEFORE_RESPONSE', provider_failure_code = :failureCode,\n       updated_at = clock_timestamp(), finished_at = clock_timestamp()\n WHERE public_id = :attemptId::uuid AND status = 'DISPATCHING'\n",
            new MapSqlParameterSource().addValue("attemptId", attemptId).addValue("failureCode", failureCode)
         );
   }

   @Transactional(transactionManager = "catalogGuideTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public void markPostResponseUncertain(UUID attemptId) {
      this.jdbc
         .update(
            "UPDATE catalog_painting_guide_generation_attempts\n   SET status = 'POST_RESPONSE_UNCERTAIN', updated_at = clock_timestamp()\n WHERE public_id = :attemptId::uuid AND status = 'DISPATCHING'\n",
            new MapSqlParameterSource("attemptId", attemptId)
         );
   }

   @Transactional(transactionManager = "catalogGuideTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public void saveSuccess(
      UUID attemptId,
      CatalogReadStore.CatalogPainting painting,
      String sourceHash,
      String canonicalResultJson,
      PaintingGuideContext context,
      KnowledgeSelection selection
   ) {
      PostgresCatalogGuideStore.InputMetadata input = this.currentInput(painting.publicId(), context, selection, sourceHash);
      UUID guideId = UUID.randomUUID();
      UUID revisionId = UUID.randomUUID();
      MapSqlParameterSource parameters = new MapSqlParameterSource()
         .addValue("guideId", guideId)
         .addValue("revisionId", revisionId)
         .addValue("paintingId", painting.publicId())
         .addValue("sourceHash", sourceHash)
         .addValue("result", canonicalResultJson)
         .addValue("batchId", input.batchId())
         .addValue("revision", input.revision())
         .addValue("snapshot", input.snapshotJson())
         .addValue("attemptId", attemptId);
      UUID resolvedGuideId = (UUID)this.jdbc
         .query(
            "INSERT INTO catalog_painting_guides\n    (public_id, painting_id, source_hash, status, result_json,\n     input_batch_id, input_revision, input_snapshot, generated_at, updated_at)\nVALUES (:guideId, :paintingId::uuid, :sourceHash, 'SUCCESS', CAST(:result AS jsonb),\n        :batchId::uuid, :revision, CAST(:snapshot AS jsonb),\n        clock_timestamp(), clock_timestamp())\nON CONFLICT (painting_id) DO UPDATE\n   SET source_hash = EXCLUDED.source_hash,\n       status = 'SUCCESS',\n       result_json = EXCLUDED.result_json,\n       input_batch_id = EXCLUDED.input_batch_id,\n       input_revision = EXCLUDED.input_revision,\n       input_snapshot = EXCLUDED.input_snapshot,\n       generated_at = EXCLUDED.generated_at,\n       updated_at = EXCLUDED.updated_at\nRETURNING public_id\n",
            parameters,
            rs -> {
               if (!rs.next()) {
                  throw new IllegalStateException("Guide cache upsert did not return an identifier");
               } else {
                  return (UUID)rs.getObject("public_id");
               }
            }
         );
      parameters.addValue("resolvedGuideId", resolvedGuideId);
      this.jdbc
         .update(
            "INSERT INTO catalog_painting_guide_result_revisions\n    (public_id, guide_id, source_hash, result_json, input_batch_id, input_revision,\n     input_snapshot, generated_at)\nVALUES (:revisionId, :resolvedGuideId::uuid, :sourceHash, CAST(:result AS jsonb),\n        :batchId::uuid, :revision, CAST(:snapshot AS jsonb), clock_timestamp())\nON CONFLICT (guide_id, source_hash) DO NOTHING\n",
            parameters
         );
      int completed = this.jdbc
         .update(
            "UPDATE catalog_painting_guide_generation_attempts\n   SET status = 'SUCCEEDED', updated_at = clock_timestamp(), finished_at = clock_timestamp()\n WHERE public_id = :attemptId::uuid AND status = 'DISPATCHING'\n",
            parameters
         );
      if (completed != 1) {
         throw new IllegalStateException("Guide provider result is not attached to a dispatching attempt");
      }
   }

   private PostgresCatalogGuideStore.InputMetadata currentInput(
      String paintingId, PaintingGuideContext context, KnowledgeSelection selection, String sourceHash
   ) {
      MapSqlParameterSource parameters = new MapSqlParameterSource("paintingId", paintingId);
      return (PostgresCatalogGuideStore.InputMetadata)this.jdbc
         .query("SELECT current_batch_id::text, current_revision\n  FROM catalog_paintings\n WHERE public_id = :paintingId::uuid\n", parameters, rs -> {
            if (!rs.next()) {
               throw new IllegalStateException("Guide painting disappeared before persistence");
            }

            try {
               Map<String, Object> snapshot = new LinkedHashMap<>();
               snapshot.put("schemaVersion", "1");
               snapshot.put("sourceHash", sourceHash);
               snapshot.put("painting", context);
               snapshot.put("knowledge", selection.items());
               snapshot.put("knowledgeFingerprints", selection.fingerprints());
               return new PostgresCatalogGuideStore.InputMetadata(rs.getString(1), rs.getInt(2), this.objectMapper.writeValueAsString(snapshot));
            } catch (JsonProcessingException exception) {
               throw new IllegalStateException("Guide input snapshot cannot be serialized", exception);
            }
         });
   }

   private RowMapper<PostgresCatalogGuideStore.GuideCacheRow> guideRowMapper() {
      return (rs, row) -> new PostgresCatalogGuideStore.GuideCacheRow(
         rs.getString("public_id"),
         rs.getString("painting_id"),
         rs.getString("source_hash"),
         rs.getString("status"),
         rs.getString("legacy_original_status"),
         rs.getString("result_json"),
         this.localTimestamp(rs.getObject("generated_at", OffsetDateTime.class)),
         this.localTimestamp(rs.getObject("updated_at", OffsetDateTime.class)),
         rs.getString("input_batch_id"),
         rs.getInt("input_revision"),
         rs.getString("input_snapshot")
      );
   }

   private LocalDateTime localTimestamp(OffsetDateTime value) {
      return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), R24_TIMESTAMP_ZONE);
   }

   private record ExistingAttempt(UUID publicId, String status) {
   }

   public record GenerationAdmission(UUID attemptId, PostgresCatalogGuideStore.GenerationAdmission.State state) {
      public static PostgresCatalogGuideStore.GenerationAdmission dispatch(UUID id) {
         return new PostgresCatalogGuideStore.GenerationAdmission(id, PostgresCatalogGuideStore.GenerationAdmission.State.DISPATCH);
      }

      public static PostgresCatalogGuideStore.GenerationAdmission inProgress(UUID id) {
         return new PostgresCatalogGuideStore.GenerationAdmission(id, PostgresCatalogGuideStore.GenerationAdmission.State.IN_PROGRESS);
      }

      public static PostgresCatalogGuideStore.GenerationAdmission uncertain(UUID id) {
         return new PostgresCatalogGuideStore.GenerationAdmission(id, PostgresCatalogGuideStore.GenerationAdmission.State.UNCERTAIN);
      }

      public enum State {
         DISPATCH,
         IN_PROGRESS,
         UNCERTAIN;
      }
   }

   public record GuideCacheRow(
      String publicId,
      String paintingId,
      String sourceHash,
      String status,
      String legacyOriginalStatus,
      String resultJson,
      LocalDateTime generatedAt,
      LocalDateTime updatedAt,
      String inputBatchId,
      int inputRevision,
      String inputSnapshot
   ) {
   }

   public record HistoricalGuideRow(
      String revisionId, String paintingId, String sourceHash, String resultJson, String inputSnapshot, LocalDateTime generatedAt, LocalDateTime updatedAt
   ) {
   }

   private record InputMetadata(String batchId, int revision, String snapshotJson) {
   }
}

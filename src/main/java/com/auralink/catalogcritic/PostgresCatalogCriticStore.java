package com.auralink.catalogcritic;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PostgresCatalogCriticStore {
   private static final String TASK_SELECT = "SELECT public_id::text AS public_id, owner_user_id, painting_id::text AS painting_id,\n       image_asset_id::text AS image_asset_id, source_image_sha256,\n       input_batch_id::text AS input_batch_id, input_revision, input_snapshot::text AS input_snapshot,\n       title_snapshot, profile, input_fingerprint, evaluator_version, model_identity,\n       prompt_schema_version, status, legacy_original_status, result_json::text AS result_json, error_code, error_message,\n       created_at, started_at, lease_expires_at, finished_at, updated_at\nFROM catalog_painting_critic_tasks\n";
   private static final String ASSET_SELECT = "SELECT public_id::text AS public_id, owner_user_id, storage_key, original_filename, mime_type,\n       file_size, content_sha256, width, height, duration_seconds, asset_type, semantic_type,\n       source_type, visibility, status, created_at, updated_at\nFROM catalog_private_media_assets\n";
   private final NamedParameterJdbcTemplate jdbc;

   PostgresCatalogCriticStore(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public PostgresCatalogCriticStore.Admission admit(
      long ownerUserId,
      String paintingId,
      String imageAssetId,
      String imageSha256,
      String titleSnapshot,
      String profile,
      String fingerprint,
      String evaluatorVersion,
      String modelIdentity,
      String promptSchemaVersion,
      String snapshotJson
   ) {
      CatalogCriticTask reusable = this.reusable(ownerUserId, paintingId, fingerprint).orElse(null);
      if (reusable != null) {
         return new PostgresCatalogCriticStore.Admission(reusable, false, "SUCCEEDED".equals(reusable.status()));
      }

      PostgresCatalogCriticStore.CatalogLocation location = this.activeLocation(paintingId, imageAssetId);
      String publicId = UUID.randomUUID().toString();
      LocalDateTime now = LocalDateTime.now();

      try {
         List<CatalogCriticTask> inserted = this.jdbc
            .query(
               "INSERT INTO catalog_painting_critic_tasks(\n    public_id, owner_user_id, painting_id, image_asset_id, source_image_sha256,\n    input_batch_id, input_revision, input_snapshot, title_snapshot, profile, input_fingerprint,\n    evaluator_version, model_identity, prompt_schema_version, status, created_at, updated_at)\nVALUES (CAST(:publicId AS uuid), :ownerUserId, CAST(:paintingId AS uuid),\n    CAST(:imageAssetId AS uuid), :imageSha256, CAST(:batchId AS uuid), :revision,\n    CAST(:snapshot AS jsonb), :titleSnapshot, :profile, :fingerprint,\n    :evaluatorVersion, :modelIdentity, :promptSchemaVersion, 'QUEUED', :now, :now)\nRETURNING public_id::text AS public_id, owner_user_id, painting_id::text AS painting_id,\n    image_asset_id::text AS image_asset_id, source_image_sha256,\n    input_batch_id::text AS input_batch_id, input_revision, input_snapshot::text AS input_snapshot,\n    title_snapshot, profile, input_fingerprint, evaluator_version, model_identity,\n    prompt_schema_version, status, legacy_original_status, result_json::text AS result_json, error_code, error_message,\n    created_at, started_at, lease_expires_at, finished_at, updated_at\n",
               new MapSqlParameterSource()
                  .addValue("publicId", publicId)
                  .addValue("ownerUserId", ownerUserId)
                  .addValue("paintingId", canonicalUuid(paintingId, ApiErrorCode.INVALID_PAINTING_ID))
                  .addValue("imageAssetId", canonicalUuid(imageAssetId, ApiErrorCode.INVALID_ASSET_ID))
                  .addValue("imageSha256", imageSha256)
                  .addValue("batchId", location.batchId())
                  .addValue("revision", location.revision())
                  .addValue("snapshot", snapshotJson)
                  .addValue("titleSnapshot", titleSnapshot)
                  .addValue("profile", profile)
                  .addValue("fingerprint", fingerprint)
                  .addValue("evaluatorVersion", evaluatorVersion)
                  .addValue("modelIdentity", modelIdentity)
                  .addValue("promptSchemaVersion", promptSchemaVersion)
                  .addValue("now", now),
               taskRow()
            );
         return new PostgresCatalogCriticStore.Admission(inserted.get(0), true, false);
      } catch (DuplicateKeyException race) {
         throw new CatalogCriticAdmissionRaceException();
      } catch (DataAccessException failure) {
         throw unavailable(failure);
      }
   }

   public Optional<CatalogCriticTask> current(long ownerUserId, String paintingId) {
      return this.queryTasks(
            "SELECT public_id::text AS public_id, owner_user_id, painting_id::text AS painting_id,\n       image_asset_id::text AS image_asset_id, source_image_sha256,\n       input_batch_id::text AS input_batch_id, input_revision, input_snapshot::text AS input_snapshot,\n       title_snapshot, profile, input_fingerprint, evaluator_version, model_identity,\n       prompt_schema_version, status, legacy_original_status, result_json::text AS result_json, error_code, error_message,\n       created_at, started_at, lease_expires_at, finished_at, updated_at\nFROM catalog_painting_critic_tasks\n WHERE owner_user_id = :ownerUserId AND painting_id = CAST(:paintingId AS uuid) ORDER BY created_at DESC, public_id DESC LIMIT 1",
            new MapSqlParameterSource()
               .addValue("ownerUserId", ownerUserId)
               .addValue("paintingId", canonicalUuid(paintingId, ApiErrorCode.INVALID_PAINTING_ID))
         )
         .stream()
         .findFirst();
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<CatalogCriticTask> existingEquivalent(long ownerUserId, String paintingId, String fingerprint) {
      return this.reusable(ownerUserId, paintingId, fingerprint);
   }

   public Optional<CatalogCriticTask> owned(String taskId, long ownerUserId) {
      return this.queryTasks(
            "SELECT public_id::text AS public_id, owner_user_id, painting_id::text AS painting_id,\n       image_asset_id::text AS image_asset_id, source_image_sha256,\n       input_batch_id::text AS input_batch_id, input_revision, input_snapshot::text AS input_snapshot,\n       title_snapshot, profile, input_fingerprint, evaluator_version, model_identity,\n       prompt_schema_version, status, legacy_original_status, result_json::text AS result_json, error_code, error_message,\n       created_at, started_at, lease_expires_at, finished_at, updated_at\nFROM catalog_painting_critic_tasks\n WHERE public_id = CAST(:taskId AS uuid) AND owner_user_id = :ownerUserId",
            new MapSqlParameterSource().addValue("taskId", canonicalUuid(taskId, ApiErrorCode.NOT_FOUND)).addValue("ownerUserId", ownerUserId)
         )
         .stream()
         .findFirst();
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public Optional<CatalogCriticTask> claim(String taskId, Duration deadline) {
      LocalDateTime now = LocalDateTime.now();
      return this.queryTasks(
            "UPDATE catalog_painting_critic_tasks\nSET status = 'RUNNING', started_at = COALESCE(started_at, :now),\n    lease_expires_at = :leaseExpiresAt, updated_at = :now\nWHERE public_id = CAST(:taskId AS uuid) AND status = 'QUEUED'\nRETURNING public_id::text AS public_id, owner_user_id, painting_id::text AS painting_id,\n    image_asset_id::text AS image_asset_id, source_image_sha256,\n    input_batch_id::text AS input_batch_id, input_revision, input_snapshot::text AS input_snapshot,\n    title_snapshot, profile, input_fingerprint, evaluator_version, model_identity,\n    prompt_schema_version, status, legacy_original_status, result_json::text AS result_json, error_code, error_message,\n    created_at, started_at, lease_expires_at, finished_at, updated_at\n",
            new MapSqlParameterSource()
               .addValue("taskId", canonicalUuid(taskId, ApiErrorCode.NOT_FOUND))
               .addValue("now", now)
               .addValue("leaseExpiresAt", now.plus(deadline))
         )
         .stream()
         .findFirst();
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public boolean succeed(String taskId, String canonicalJson) {
      return this.update(
            "UPDATE catalog_painting_critic_tasks\nSET result_json = CAST(:resultJson AS jsonb), status = 'SUCCEEDED', error_code = NULL,\n    error_message = NULL, lease_expires_at = NULL, finished_at = :now, updated_at = :now\nWHERE public_id = CAST(:taskId AS uuid) AND status = 'RUNNING'\n",
            new MapSqlParameterSource()
               .addValue("taskId", canonicalUuid(taskId, ApiErrorCode.NOT_FOUND))
               .addValue("resultJson", canonicalJson)
               .addValue("now", LocalDateTime.now())
         )
         == 1;
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public boolean fail(String taskId, String code, String message) {
      return this.update(
            "UPDATE catalog_painting_critic_tasks\nSET status = 'FAILED', error_code = :code, error_message = :message,\n    lease_expires_at = NULL, finished_at = :now, updated_at = :now\nWHERE public_id = CAST(:taskId AS uuid) AND status = 'RUNNING'\n",
            new MapSqlParameterSource()
               .addValue("taskId", canonicalUuid(taskId, ApiErrorCode.NOT_FOUND))
               .addValue("code", code)
               .addValue("message", message)
               .addValue("now", LocalDateTime.now())
         )
         == 1;
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager", propagation = Propagation.REQUIRES_NEW)
   public int terminalizeExpiredRunning() {
      return this.update(
         "UPDATE catalog_painting_critic_tasks\nSET status = 'FAILED', error_code = 'INTERRUPTED',\n    error_message = '智能评析任务在服务中断后未完成，请手动重新发起',\n    lease_expires_at = NULL, finished_at = :now, updated_at = :now\nWHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at <= :now\n",
         new MapSqlParameterSource("now", LocalDateTime.now())
      );
   }

   public Optional<CatalogPrivateMediaAsset> findPrivateAsset(String assetId) {
      return this.queryAssets(
            "SELECT public_id::text AS public_id, owner_user_id, storage_key, original_filename, mime_type,\n       file_size, content_sha256, width, height, duration_seconds, asset_type, semantic_type,\n       source_type, visibility, status, created_at, updated_at\nFROM catalog_private_media_assets\n WHERE public_id = CAST(:assetId AS uuid)",
            new MapSqlParameterSource("assetId", canonicalUuid(assetId, ApiErrorCode.INVALID_ASSET_ID))
         )
         .stream()
         .findFirst();
   }

   private Optional<CatalogCriticTask> reusable(long ownerUserId, String paintingId, String fingerprint) {
      return this.queryTasks(
            "SELECT public_id::text AS public_id, owner_user_id, painting_id::text AS painting_id,\n       image_asset_id::text AS image_asset_id, source_image_sha256,\n       input_batch_id::text AS input_batch_id, input_revision, input_snapshot::text AS input_snapshot,\n       title_snapshot, profile, input_fingerprint, evaluator_version, model_identity,\n       prompt_schema_version, status, legacy_original_status, result_json::text AS result_json, error_code, error_message,\n       created_at, started_at, lease_expires_at, finished_at, updated_at\nFROM catalog_painting_critic_tasks\n WHERE owner_user_id = :ownerUserId AND painting_id = CAST(:paintingId AS uuid) AND input_fingerprint = :fingerprint AND status IN ('SUCCEEDED', 'QUEUED', 'RUNNING') ORDER BY created_at DESC LIMIT 1",
            new MapSqlParameterSource()
               .addValue("ownerUserId", ownerUserId)
               .addValue("paintingId", canonicalUuid(paintingId, ApiErrorCode.INVALID_PAINTING_ID))
               .addValue("fingerprint", fingerprint)
         )
         .stream()
         .findFirst();
   }

   private PostgresCatalogCriticStore.CatalogLocation activeLocation(String paintingId, String imageAssetId) {
      try {
         return (PostgresCatalogCriticStore.CatalogLocation)this.jdbc
            .query(
               "SELECT current_batch_id::text AS batch_id, current_revision\nFROM catalog_paintings\nWHERE public_id = CAST(:paintingId AS uuid) AND image_asset_id = CAST(:imageAssetId AS uuid)\n  AND catalog_status = 'ACTIVE' AND image_display_status = 'READY'\n",
               new MapSqlParameterSource()
                  .addValue("paintingId", canonicalUuid(paintingId, ApiErrorCode.INVALID_PAINTING_ID))
                  .addValue("imageAssetId", canonicalUuid(imageAssetId, ApiErrorCode.INVALID_ASSET_ID)),
               (rs, row) -> new PostgresCatalogCriticStore.CatalogLocation(rs.getString("batch_id"), rs.getInt("current_revision"))
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiV1Exception(HttpStatus.CONFLICT, ApiErrorCode.CRITIC_INVALID_RESPONSE, "该画作当前没有可供评析的有效图像"));
      } catch (ApiV1Exception exception) {
         throw exception;
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private List<CatalogCriticTask> queryTasks(String sql, MapSqlParameterSource parameters) {
      try {
         return this.jdbc.query(sql, parameters, taskRow());
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private List<CatalogPrivateMediaAsset> queryAssets(String sql, MapSqlParameterSource parameters) {
      try {
         return this.jdbc.query(sql, parameters, assetRow());
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private int update(String sql, MapSqlParameterSource parameters) {
      try {
         return this.jdbc.update(sql, parameters);
      } catch (DataAccessException exception) {
         throw unavailable(exception);
      }
   }

   private static RowMapper<CatalogCriticTask> taskRow() {
      return (rs, row) -> new CatalogCriticTask(
         rs.getString("public_id"),
         rs.getLong("owner_user_id"),
         rs.getString("painting_id"),
         rs.getString("image_asset_id"),
         rs.getString("source_image_sha256"),
         rs.getString("input_batch_id"),
         rs.getInt("input_revision"),
         rs.getString("input_snapshot"),
         rs.getString("title_snapshot"),
         rs.getString("profile"),
         rs.getString("input_fingerprint"),
         rs.getString("evaluator_version"),
         rs.getString("model_identity"),
         rs.getString("prompt_schema_version"),
         rs.getString("status"),
         rs.getString("legacy_original_status"),
         rs.getString("result_json"),
         rs.getString("error_code"),
         rs.getString("error_message"),
         localDateTime(rs, "created_at"),
         localDateTime(rs, "started_at"),
         localDateTime(rs, "lease_expires_at"),
         localDateTime(rs, "finished_at"),
         localDateTime(rs, "updated_at")
      );
   }

   private static RowMapper<CatalogPrivateMediaAsset> assetRow() {
      return (rs, row) -> new CatalogPrivateMediaAsset(
         rs.getString("public_id"),
         rs.getLong("owner_user_id"),
         rs.getString("storage_key"),
         rs.getString("original_filename"),
         rs.getString("mime_type"),
         rs.getLong("file_size"),
         rs.getString("content_sha256"),
         nullableInt(rs, "width"),
         nullableInt(rs, "height"),
         nullableDouble(rs, "duration_seconds"),
         rs.getString("asset_type"),
         rs.getString("semantic_type"),
         rs.getString("source_type"),
         rs.getString("visibility"),
         rs.getString("status"),
         localDateTime(rs, "created_at"),
         localDateTime(rs, "updated_at")
      );
   }

   private static LocalDateTime localDateTime(ResultSet rs, String name) throws SQLException {
      Timestamp value = rs.getTimestamp(name);
      return value == null ? null : value.toLocalDateTime();
   }

   private static Integer nullableInt(ResultSet rs, String name) throws SQLException {
      int value = rs.getInt(name);
      return rs.wasNull() ? null : value;
   }

   private static Double nullableDouble(ResultSet rs, String name) throws SQLException {
      double value = rs.getDouble(name);
      return rs.wasNull() ? null : value;
   }

   private static String canonicalUuid(String value, ApiErrorCode code) {
      try {
         String canonical = UUID.fromString(value).toString();
         if (!canonical.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("non-canonical UUID");
         } else {
            return canonical;
         }
      } catch (RuntimeException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, code, "资源标识格式无效");
      }
   }

   private static ApiV1Exception unavailable(DataAccessException cause) {
      return new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_READ_UNAVAILABLE, "PostgreSQL Critic 数据库暂不可用");
   }

   public record Admission(CatalogCriticTask task, boolean dispatchRequired, boolean reusedSuccess) {
   }

   private record CatalogLocation(String batchId, int revision) {
   }
}

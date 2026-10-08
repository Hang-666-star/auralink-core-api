package com.auralink.catalogcritic;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import java.time.LocalDateTime;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresPrivateMediaUploadStore {
   private final NamedParameterJdbcTemplate jdbc;

   PostgresPrivateMediaUploadStore(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public void insert(CatalogPrivateMediaAsset asset, String managedStorageKey) {
      LocalDateTime now = asset.createdAt() == null ? LocalDateTime.now() : asset.createdAt();
      MapSqlParameterSource values = new MapSqlParameterSource()
         .addValue("publicId", asset.publicId())
         .addValue("ownerUserId", asset.ownerUserId())
         .addValue("privateStorageKey", asset.storageKey())
         .addValue("managedStorageKey", managedStorageKey)
         .addValue("originalFilename", asset.originalFilename())
         .addValue("mimeType", asset.mimeType())
         .addValue("fileSize", asset.fileSize())
         .addValue("contentSha256", asset.contentSha256())
         .addValue("width", asset.width())
         .addValue("height", asset.height())
         .addValue("durationSeconds", asset.durationSeconds())
         .addValue("assetType", asset.assetType())
         .addValue("semanticType", asset.semanticType())
         .addValue("sourceType", asset.sourceType())
         .addValue("visibility", asset.visibility())
         .addValue("status", asset.status())
         .addValue("now", now);

      try {
         this.jdbc
            .update(
               "INSERT INTO catalog_private_media_assets(\n    public_id, owner_user_id, storage_key, original_filename, mime_type, file_size,\n    content_sha256, width, height, duration_seconds, asset_type, semantic_type, source_type,\n    visibility, status, created_at, updated_at)\nVALUES (CAST(:publicId AS uuid), :ownerUserId, :privateStorageKey, :originalFilename, :mimeType,\n    :fileSize, :contentSha256, :width, :height, :durationSeconds, :assetType, :semanticType,\n    :sourceType, :visibility, :status, :now, :now)\n",
               values
            );
         this.jdbc
            .update(
               "INSERT INTO media_assets(\n    public_id, owner_user_id, storage_key, original_filename, mime_type, file_size,\n    sha256, width, height, duration_seconds, asset_type, semantic_type, source_type,\n    visibility, status, created_at, updated_at)\nVALUES (:publicId, :ownerUserId, :managedStorageKey, :originalFilename, :mimeType,\n    :fileSize, :contentSha256, :width, :height, :durationSeconds, :assetType, :semanticType,\n    :sourceType, :visibility, :status, :now, :now)\n",
               values
            );
      } catch (DataAccessException exception) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_READ_UNAVAILABLE, "PostgreSQL 私有媒体元数据暂不可用");
      }
   }
}

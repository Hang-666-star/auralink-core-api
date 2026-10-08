package com.auralink.api.v1.media;

import com.auralink.catalogcritic.CatalogPrivateMediaAsset;
import com.auralink.catalogread.CatalogMediaService;
import com.auralink.entity.MediaAsset;
import java.time.LocalDateTime;

public record MediaAssetResponse(
   String assetId,
   String originalFilename,
   String mimeType,
   Long fileSize,
   Integer width,
   Integer height,
   Double durationSeconds,
   String assetType,
   String semanticType,
   String sourceType,
   String visibility,
   String status,
   String contentUrl,
   String downloadUrl,
   LocalDateTime createdAt,
   LocalDateTime updatedAt
) {
   public static MediaAssetResponse from(MediaAsset asset) {
      String publicId = asset.getPublicId();
      String baseUrl = "/api/v1/assets/" + publicId;
      return new MediaAssetResponse(
         publicId,
         asset.getOriginalFilename(),
         asset.getMimeType(),
         asset.getFileSize(),
         asset.getWidth(),
         asset.getHeight(),
         asset.getDurationSeconds(),
         asset.getAssetType(),
         asset.getSemanticType(),
         asset.getSourceType(),
         asset.getVisibility(),
         asset.getStatus(),
         baseUrl + "/content",
         baseUrl + "/download",
         asset.getCreatedAt(),
         asset.getUpdatedAt()
      );
   }

   public static MediaAssetResponse fromCatalog(CatalogMediaService.CatalogMediaContent content) {
      String publicId = content.asset().publicId();
      String baseUrl = "/api/v1/assets/" + publicId;
      return new MediaAssetResponse(
         publicId,
         content.filename(),
         content.mimeType(),
         content.size(),
         null,
         null,
         null,
         "IMAGE",
         "PAINTING",
         "CATALOG_REFERENCE",
         "PUBLIC",
         "ACTIVE",
         baseUrl + "/content",
         baseUrl + "/download",
         null,
         null
      );
   }

   public static MediaAssetResponse fromCatalogPrivate(CatalogPrivateMediaAsset asset) {
      String baseUrl = "/api/v1/assets/" + asset.publicId();
      return new MediaAssetResponse(
         asset.publicId(),
         asset.originalFilename(),
         asset.mimeType(),
         asset.fileSize(),
         asset.width(),
         asset.height(),
         asset.durationSeconds(),
         asset.assetType(),
         asset.semanticType(),
         asset.sourceType(),
         asset.visibility(),
         asset.status(),
         baseUrl + "/content",
         baseUrl + "/download",
         asset.createdAt(),
         asset.updatedAt()
      );
   }
}

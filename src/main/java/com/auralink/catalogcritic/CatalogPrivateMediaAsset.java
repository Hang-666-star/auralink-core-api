package com.auralink.catalogcritic;

import java.time.LocalDateTime;

public record CatalogPrivateMediaAsset(
   String publicId,
   long ownerUserId,
   String storageKey,
   String originalFilename,
   String mimeType,
   long fileSize,
   String contentSha256,
   Integer width,
   Integer height,
   Double durationSeconds,
   String assetType,
   String semanticType,
   String sourceType,
   String visibility,
   String status,
   LocalDateTime createdAt,
   LocalDateTime updatedAt
) {
}

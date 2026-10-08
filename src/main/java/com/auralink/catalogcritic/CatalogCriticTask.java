package com.auralink.catalogcritic;

import java.time.LocalDateTime;

public record CatalogCriticTask(
   String publicId,
   long ownerUserId,
   String paintingId,
   String imageAssetId,
   String sourceImageSha256,
   String inputBatchId,
   int inputRevision,
   String inputSnapshotJson,
   String titleSnapshot,
   String profile,
   String inputFingerprint,
   String evaluatorVersion,
   String modelIdentity,
   String promptSchemaVersion,
   String status,
   String legacyOriginalStatus,
   String resultJson,
   String errorCode,
   String errorMessage,
   LocalDateTime createdAt,
   LocalDateTime startedAt,
   LocalDateTime leaseExpiresAt,
   LocalDateTime finishedAt,
   LocalDateTime updatedAt
) {
}

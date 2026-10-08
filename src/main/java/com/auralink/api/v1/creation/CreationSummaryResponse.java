package com.auralink.api.v1.creation;

import com.auralink.creation.CreationRecoveryState;
import com.auralink.creation.CreationStatus;
import com.auralink.workflow.WorkflowModality;

public record CreationSummaryResponse(
   String creationId,
   String workflowId,
   String workflowName,
   CreationStatus status,
   String errorCode,
   String errorMessage,
   CreationRecoveryState recoveryState,
   WorkflowModality sourceModality,
   String sourcePaintingId,
   String sourceAssetId,
   WorkflowModality finalModality,
   String finalAssetId,
   String finalAssetContentUrl,
   String finalAssetDownloadUrl,
   String createdAt,
   String updatedAt,
   String startedAt,
   String finishedAt,
   int retryVersion,
   boolean retryAvailable,
   String retryBlockedReason,
   long executionAttemptCount
) {
}

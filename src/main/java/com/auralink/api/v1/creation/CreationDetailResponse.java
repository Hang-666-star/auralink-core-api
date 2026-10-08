package com.auralink.api.v1.creation;

import com.auralink.creation.CreationRecoveryState;
import com.auralink.creation.CreationStatus;
import com.auralink.workflow.WorkflowModality;
import java.util.List;

public record CreationDetailResponse(
   String creationId,
   String workflowId,
   String workflowName,
   CreationStatus status,
   String errorCode,
   String errorMessage,
   CreationRecoveryState recoveryState,
   WorkflowModality sourceModality,
   String sourcePaintingId,
   String sourcePaintingTitle,
   String sourcePaintingContentUrl,
   String sourceAssetId,
   String sourceText,
   String sourceAssetContentUrl,
   String sourceAssetDownloadUrl,
   WorkflowModality finalModality,
   String finalAssetId,
   String finalAssetContentUrl,
   String finalAssetDownloadUrl,
   String finalText,
   CreationPoemResponse finalPoem,
   String createdAt,
   String updatedAt,
   String startedAt,
   String finishedAt,
   int retryVersion,
   boolean retryAvailable,
   String retryBlockedReason,
   long executionAttemptCount,
   List<CreationStepSummaryResponse> steps
) {
   public CreationDetailResponse {
      steps = List.copyOf(steps);
   }
}

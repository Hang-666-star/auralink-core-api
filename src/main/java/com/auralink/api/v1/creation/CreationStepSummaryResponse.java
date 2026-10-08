package com.auralink.api.v1.creation;

import com.auralink.creation.CreationStepStatus;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;

public record CreationStepSummaryResponse(
   String stepId,
   int stepIndex,
   String nodeId,
   WorkflowOperation operation,
   WorkflowModality inputModality,
   WorkflowModality outputModality,
   CreationStepStatus status,
   int attemptCount,
   String errorCode,
   String errorMessage,
   String outputAssetId,
   String outputAssetContentUrl,
   String outputAssetDownloadUrl,
   String outputText,
   CreationPoemResponse outputPoem,
   Integer requestedDurationSeconds,
   String startedAt,
   String finishedAt
) {
}

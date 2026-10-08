package com.auralink.api.v1.workflow;

import com.auralink.workflow.WorkflowModality;

public record WorkflowSummaryResponse(
   String workflowId,
   String name,
   String description,
   int schemaVersion,
   WorkflowModality sourceModality,
   WorkflowModality terminalModality,
   int nodeCount,
   String status,
   boolean conversionRequired,
   String updatedAt,
   String createdAt
) {
}

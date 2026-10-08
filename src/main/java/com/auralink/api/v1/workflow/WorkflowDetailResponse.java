package com.auralink.api.v1.workflow;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import java.util.List;

public record WorkflowDetailResponse(
   String workflowId,
   String name,
   String description,
   int schemaVersion,
   CanonicalWorkflowGraph graph,
   WorkflowModality sourceModality,
   WorkflowModality terminalModality,
   int nodeCount,
   int edgeCount,
   List<WorkflowOperation> operationSequence,
   String status,
   boolean conversionRequired,
   String createdAt,
   String updatedAt
) {
   public WorkflowDetailResponse {
      operationSequence = List.copyOf(operationSequence);
   }
}

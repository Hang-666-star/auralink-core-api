package com.auralink.api.v1.workflow;

import com.auralink.api.v1.error.ApiViolationDetail;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.auralink.workflow.graph.WorkflowCanonicalization;
import com.auralink.workflow.graph.WorkflowValidationResult;
import java.util.List;

public record WorkflowValidationResponse(
   boolean valid,
   int schemaVersion,
   CanonicalWorkflowGraph canonicalGraph,
   WorkflowModality sourceModality,
   WorkflowModality terminalModality,
   int nodeCount,
   int edgeCount,
   List<WorkflowOperation> operationSequence,
   List<ApiViolationDetail> violations
) {
   public WorkflowValidationResponse {
      operationSequence = List.copyOf(operationSequence);
      violations = List.copyOf(violations);
   }

   public static WorkflowValidationResponse from(WorkflowValidationResult validation, int supportedSchemaVersion) {
      WorkflowCanonicalization canonical = validation.canonicalization();
      return canonical == null
         ? new WorkflowValidationResponse(false, supportedSchemaVersion, null, null, null, 0, 0, List.of(), validation.violations())
         : new WorkflowValidationResponse(
            true,
            canonical.graph().schemaVersion(),
            canonical.graph(),
            canonical.sourceModality(),
            canonical.terminalModality(),
            canonical.nodeCount(),
            canonical.edgeCount(),
            canonical.operationSequence(),
            List.of()
         );
   }
}

package com.auralink.workflow.service;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import java.util.List;

public record StoredWorkflowDefinition(
   CanonicalWorkflowGraph graph, WorkflowModality sourceModality, WorkflowModality terminalModality, List<WorkflowOperation> operationSequence
) {
   public StoredWorkflowDefinition {
      operationSequence = List.copyOf(operationSequence);
   }
}

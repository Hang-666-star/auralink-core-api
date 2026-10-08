package com.auralink.workflow.graph;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import java.util.List;

public record WorkflowCanonicalization(
   CanonicalWorkflowGraph graph,
   String canonicalJson,
   WorkflowModality sourceModality,
   WorkflowModality terminalModality,
   List<WorkflowOperation> operationSequence
) {
   public WorkflowCanonicalization {
      operationSequence = List.copyOf(operationSequence);
   }

   public int nodeCount() {
      return this.graph.nodes().size();
   }

   public int edgeCount() {
      return this.graph.edges().size();
   }
}

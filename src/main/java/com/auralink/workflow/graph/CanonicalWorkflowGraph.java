package com.auralink.workflow.graph;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder({"schemaVersion", "nodes", "edges"})
public record CanonicalWorkflowGraph(int schemaVersion, List<CanonicalWorkflowNode> nodes, List<CanonicalWorkflowEdge> edges) {
   public CanonicalWorkflowGraph {
      nodes = List.copyOf(nodes);
      edges = List.copyOf(edges);
   }
}

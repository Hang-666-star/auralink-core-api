package com.auralink.workflow.snapshot;

import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"snapshotVersion", "workflowId", "workflowName", "workflowSchemaVersion", "graph"})
public record WorkflowSnapshot(int snapshotVersion, String workflowId, String workflowName, int workflowSchemaVersion, CanonicalWorkflowGraph graph) {
}

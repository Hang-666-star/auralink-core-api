package com.auralink.workflow.service;

import com.auralink.api.v1.workflow.WorkflowDefinitionRequest;
import com.auralink.entity.UserWorkflow;
import com.auralink.workflow.WorkflowNodeKind;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.auralink.workflow.graph.CanonicalWorkflowNode;
import com.auralink.workflow.graph.WorkflowEdgeRequest;
import com.auralink.workflow.graph.WorkflowGraphRequest;
import com.auralink.workflow.graph.WorkflowNodeRequest;
import com.auralink.workflow.graph.WorkflowParameters;

public final class WorkflowStoredRequestFactory {
   private WorkflowStoredRequestFactory() {
   }

   public static WorkflowDefinitionRequest from(UserWorkflow workflow, CanonicalWorkflowGraph graph) {
      WorkflowDefinitionRequest request = new WorkflowDefinitionRequest();
      request.setName(workflow.getName());
      request.setDescription(workflow.getDescription());
      WorkflowGraphRequest requestedGraph = new WorkflowGraphRequest();
      requestedGraph.setSchemaVersion(graph.schemaVersion());
      requestedGraph.setNodes(graph.nodes().stream().map(WorkflowStoredRequestFactory::node).toList());
      requestedGraph.setEdges(graph.edges().stream().map(edge -> {
         WorkflowEdgeRequest requestedEdge = new WorkflowEdgeRequest();
         requestedEdge.setFrom(edge.from());
         requestedEdge.setTo(edge.to());
         return requestedEdge;
      }).toList());
      request.setGraph(requestedGraph);
      return request;
   }

   private static WorkflowNodeRequest node(CanonicalWorkflowNode node) {
      WorkflowNodeRequest requestedNode = new WorkflowNodeRequest();
      requestedNode.setId(node.id());
      requestedNode.setKind(node.kind().name());
      requestedNode.setOutputModality(node.outputModality().name());
      if (node.kind() == WorkflowNodeKind.TRANSFORM) {
         requestedNode.setOperation(node.operation().name());
         requestedNode.setProviderCode(node.providerCode());
         requestedNode.setInputModality(node.inputModality().name());
         WorkflowParameters parameters = new WorkflowParameters();
         if (node.parameters() != null) {
            node.parameters().forEach(parameters::put);
         }

         requestedNode.setParameters(parameters);
      }

      return requestedNode;
   }
}

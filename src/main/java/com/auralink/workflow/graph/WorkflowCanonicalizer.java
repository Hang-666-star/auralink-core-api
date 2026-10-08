package com.auralink.workflow.graph;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowNodeKind;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.capability.WorkflowCapabilityRegistry;
import com.auralink.workflow.capability.WorkflowProviderCapability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class WorkflowCanonicalizer {
   private final WorkflowGraphCodec codec;
   private final WorkflowCapabilityRegistry registry;

   public WorkflowCanonicalization canonicalize(WorkflowGraphRequest request) {
      Map<String, WorkflowNodeRequest> nodesById = new LinkedHashMap<>();
      request.getNodes().forEach(node -> nodesById.put(node.getId(), node));
      Map<String, String> nextById = new LinkedHashMap<>();
      request.getEdges().forEach(edge -> nextById.put(edge.getFrom(), edge.getTo()));
      WorkflowNodeRequest current = request.getNodes()
         .stream()
         .filter(node -> WorkflowNodeKind.SOURCE.name().equals(node.getKind()))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException("Valid graph has no source"));
      List<CanonicalWorkflowNode> orderedNodes = new ArrayList<>();
      List<CanonicalWorkflowEdge> orderedEdges = new ArrayList<>();
      List<WorkflowOperation> operations = new ArrayList<>();
      WorkflowModality sourceModality = WorkflowModality.valueOf(current.getOutputModality());
      WorkflowModality terminalModality = sourceModality;

      while (current != null) {
         CanonicalWorkflowNode canonicalNode;
         if (WorkflowNodeKind.SOURCE.name().equals(current.getKind())) {
            canonicalNode = CanonicalWorkflowNode.source(current.getId(), WorkflowModality.valueOf(current.getOutputModality()));
         } else {
            WorkflowOperation operation = WorkflowOperation.valueOf(current.getOperation());
            operations.add(operation);
            canonicalNode = CanonicalWorkflowNode.transform(
               current.getId(),
               operation,
               current.getProviderCode(),
               WorkflowModality.valueOf(current.getInputModality()),
               WorkflowModality.valueOf(current.getOutputModality()),
               this.canonicalParameters(request.getSchemaVersion(), current, operation)
            );
         }

         orderedNodes.add(canonicalNode);
         terminalModality = canonicalNode.outputModality();
         String nextId = nextById.get(current.getId());
         if (nextId == null) {
            current = null;
         } else {
            orderedEdges.add(new CanonicalWorkflowEdge(current.getId(), nextId));
            current = nodesById.get(nextId);
         }
      }

      CanonicalWorkflowGraph graph = new CanonicalWorkflowGraph(request.getSchemaVersion(), orderedNodes, orderedEdges);
      return new WorkflowCanonicalization(graph, this.codec.encode(graph), sourceModality, terminalModality, operations);
   }

   private Map<String, JsonNode> canonicalParameters(Integer schemaVersion, WorkflowNodeRequest node, WorkflowOperation operation) {
      Map<String, JsonNode> values = new TreeMap<>();
      if (node.getParameters() != null) {
         values.putAll(node.getParameters().values());
      }

      if (!Integer.valueOf(2).equals(schemaVersion)) {
         return values;
      }

      this.registry
         .find(operation.name())
         .flatMap(capability -> capability.providers().stream().filter(provider -> provider.code().equals(node.getProviderCode())).findFirst())
         .ifPresent(provider -> this.applyDefaults(values, provider));
      return values;
   }

   private void applyDefaults(Map<String, JsonNode> values, WorkflowProviderCapability provider) {
      provider.parameterSchema().properties().forEach((name, definition) -> {
         if (!values.containsKey(name) && definition.defaultValue() != null) {
            if ("integer".equals(definition.type())) {
               values.put(name, IntNode.valueOf(definition.defaultValue()));
            }
         }
      });
   }

   public WorkflowCanonicalizer(final WorkflowGraphCodec codec, final WorkflowCapabilityRegistry registry) {
      this.codec = codec;
      this.registry = registry;
   }
}

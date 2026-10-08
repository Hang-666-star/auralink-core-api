package com.auralink.workflow.graph;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@JsonPropertyOrder({"schemaVersion", "nodes", "edges"})
public final class WorkflowGraphRequest {
   private Integer schemaVersion;
   private List<WorkflowNodeRequest> nodes;
   private List<WorkflowEdgeRequest> edges;
   @JsonIgnore
   private final Map<String, JsonNode> unknownFields = new TreeMap<>();

   @JsonAnySetter
   public void putUnknownField(String name, JsonNode value) {
      this.unknownFields.put(name, value);
   }

   @JsonAnyGetter
   public Map<String, JsonNode> unknownFields() {
      return Collections.unmodifiableMap(this.unknownFields);
   }

   public Integer getSchemaVersion() {
      return this.schemaVersion;
   }

   public List<WorkflowNodeRequest> getNodes() {
      return this.nodes;
   }

   public List<WorkflowEdgeRequest> getEdges() {
      return this.edges;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }

   public void setSchemaVersion(final Integer schemaVersion) {
      this.schemaVersion = schemaVersion;
   }

   public void setNodes(final List<WorkflowNodeRequest> nodes) {
      this.nodes = nodes;
   }

   public void setEdges(final List<WorkflowEdgeRequest> edges) {
      this.edges = edges;
   }
}

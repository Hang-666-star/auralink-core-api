package com.auralink.api.v1.workflow;

import com.auralink.workflow.graph.WorkflowGraphRequest;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@JsonPropertyOrder({"name", "description", "status", "graph"})
public final class WorkflowDefinitionRequest {
   private String name;
   private String description;
   private String status;
   private WorkflowGraphRequest graph;
   @JsonIgnore
   private boolean statusPresent;
   @JsonIgnore
   private final Map<String, JsonNode> unknownFields = new TreeMap<>();

   public void setStatus(String status) {
      this.status = status;
      this.statusPresent = status != null;
   }

   public boolean hasStatusField() {
      return this.statusPresent;
   }

   @JsonAnySetter
   public void putUnknownField(String name, JsonNode value) {
      this.unknownFields.put(name, value);
   }

   @JsonAnyGetter
   public Map<String, JsonNode> unknownFields() {
      return Collections.unmodifiableMap(this.unknownFields);
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public String getStatus() {
      return this.status;
   }

   public WorkflowGraphRequest getGraph() {
      return this.graph;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }

   public void setName(final String name) {
      this.name = name;
   }

   public void setDescription(final String description) {
      this.description = description;
   }

   public void setGraph(final WorkflowGraphRequest graph) {
      this.graph = graph;
   }
}

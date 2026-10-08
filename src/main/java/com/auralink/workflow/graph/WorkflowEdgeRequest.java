package com.auralink.workflow.graph;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@JsonPropertyOrder({"from", "to"})
public final class WorkflowEdgeRequest {
   private String from;
   private String to;
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

   public String getFrom() {
      return this.from;
   }

   public String getTo() {
      return this.to;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }

   public void setFrom(final String from) {
      this.from = from;
   }

   public void setTo(final String to) {
      this.to = to;
   }
}

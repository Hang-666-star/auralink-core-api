package com.auralink.api.v1.creation;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@JsonPropertyOrder({"workflowId", "source"})
public final class CreationSubmissionRequest {
   private String workflowId;
   private CreationSourceRequest source;
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

   public String getWorkflowId() {
      return this.workflowId;
   }

   public CreationSourceRequest getSource() {
      return this.source;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }

   public void setWorkflowId(final String workflowId) {
      this.workflowId = workflowId;
   }

   public void setSource(final CreationSourceRequest source) {
      this.source = source;
   }
}

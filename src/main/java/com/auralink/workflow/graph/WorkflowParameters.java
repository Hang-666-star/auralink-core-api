package com.auralink.workflow.graph;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class WorkflowParameters {
   private final Map<String, JsonNode> values = new TreeMap<>();

   @JsonAnySetter
   public void put(String name, JsonNode value) {
      this.values.put(name, value);
   }

   @JsonAnyGetter
   public Map<String, JsonNode> values() {
      return Collections.unmodifiableMap(this.values);
   }

   @JsonIgnore
   public boolean isEmpty() {
      return this.values.isEmpty();
   }
}

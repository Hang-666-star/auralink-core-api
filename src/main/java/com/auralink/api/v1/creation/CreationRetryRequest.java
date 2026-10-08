package com.auralink.api.v1.creation;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class CreationRetryRequest {
   private Integer expectedRetryVersion;
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

   public Integer getExpectedRetryVersion() {
      return this.expectedRetryVersion;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }

   public void setExpectedRetryVersion(final Integer expectedRetryVersion) {
      this.expectedRetryVersion = expectedRetryVersion;
   }
}

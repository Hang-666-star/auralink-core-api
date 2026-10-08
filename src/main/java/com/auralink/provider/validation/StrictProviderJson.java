package com.auralink.provider.validation;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public final class StrictProviderJson {
   private StrictProviderJson() {
   }

   public static JsonNode parse(ObjectMapper mapper, String json) throws IOException {
      return (JsonNode)mapper.readerFor(JsonNode.class)
         .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
         .with(Feature.STRICT_DUPLICATE_DETECTION)
         .readValue(json);
   }

   public static JsonNode parse(ObjectMapper mapper, byte[] json) throws IOException {
      return (JsonNode)mapper.readerFor(JsonNode.class)
         .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
         .with(Feature.STRICT_DUPLICATE_DETECTION)
         .readValue(json);
   }
}

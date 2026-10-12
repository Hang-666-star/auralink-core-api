package com.auralink.api.v1.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ChinaMapResponse(String type, List<Feature> features, String sha256) {
   public record Feature(String type, Properties properties, Geometry geometry) {}
   public record Properties(String name) {}
   public record Geometry(String type, JsonNode coordinates) {}
}

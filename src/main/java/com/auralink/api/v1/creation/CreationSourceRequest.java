package com.auralink.api.v1.creation;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@JsonPropertyOrder({"modality", "text", "assetId", "paintingId"})
public final class CreationSourceRequest {
   private String modality;
   private String text;
   private String assetId;
   private String paintingId;
   @JsonIgnore
   private boolean textPresent;
   @JsonIgnore
   private boolean assetIdPresent;
   @JsonIgnore
   private boolean paintingIdPresent;
   @JsonIgnore
   private final Map<String, JsonNode> unknownFields = new TreeMap<>();

   public void setModality(String modality) {
      this.modality = modality;
   }

   public void setText(String text) {
      this.text = text;
      this.textPresent = true;
   }

   public void setAssetId(String assetId) {
      this.assetId = assetId;
      this.assetIdPresent = true;
   }

   public void setPaintingId(String paintingId) {
      this.paintingId = paintingId;
      this.paintingIdPresent = true;
   }

   public boolean hasTextField() {
      return this.textPresent;
   }

   public boolean hasAssetIdField() {
      return this.assetIdPresent;
   }

   public boolean hasPaintingIdField() {
      return this.paintingIdPresent;
   }

   @JsonAnySetter
   public void putUnknownField(String name, JsonNode value) {
      this.unknownFields.put(name, value);
   }

   @JsonAnyGetter
   public Map<String, JsonNode> unknownFields() {
      return Collections.unmodifiableMap(this.unknownFields);
   }

   public String getModality() {
      return this.modality;
   }

   public String getText() {
      return this.text;
   }

   public String getAssetId() {
      return this.assetId;
   }

   public String getPaintingId() {
      return this.paintingId;
   }

   public boolean isTextPresent() {
      return this.textPresent;
   }

   public boolean isAssetIdPresent() {
      return this.assetIdPresent;
   }

   public boolean isPaintingIdPresent() {
      return this.paintingIdPresent;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }
}

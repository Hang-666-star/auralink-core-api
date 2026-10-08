package com.auralink.workflow.graph;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"id", "kind", "operation", "providerCode", "inputModality", "outputModality", "parameters"})
public final class WorkflowNodeRequest {
   private String id;
   private String kind;
   private String operation;
   private String providerCode;
   private String inputModality;
   private String outputModality;
   private WorkflowParameters parameters;
   @JsonIgnore
   private boolean operationPresent;
   @JsonIgnore
   private boolean providerCodePresent;
   @JsonIgnore
   private boolean inputModalityPresent;
   @JsonIgnore
   private boolean parametersPresent;
   @JsonIgnore
   private final Map<String, JsonNode> unknownFields = new TreeMap<>();

   public void setOperation(String operation) {
      this.operation = operation;
      this.operationPresent = true;
   }

   public void setProviderCode(String providerCode) {
      this.providerCode = providerCode;
      this.providerCodePresent = true;
   }

   public void setInputModality(String inputModality) {
      this.inputModality = inputModality;
      this.inputModalityPresent = true;
   }

   public void setParameters(WorkflowParameters parameters) {
      this.parameters = parameters;
      this.parametersPresent = true;
   }

   public boolean hasOperationField() {
      return this.operationPresent;
   }

   public boolean hasProviderCodeField() {
      return this.providerCodePresent;
   }

   public boolean hasInputModalityField() {
      return this.inputModalityPresent;
   }

   public boolean hasParametersField() {
      return this.parametersPresent;
   }

   @JsonAnySetter
   public void putUnknownField(String name, JsonNode value) {
      this.unknownFields.put(name, value);
   }

   @JsonAnyGetter
   public Map<String, JsonNode> unknownFields() {
      return Collections.unmodifiableMap(this.unknownFields);
   }

   public String getId() {
      return this.id;
   }

   public String getKind() {
      return this.kind;
   }

   public String getOperation() {
      return this.operation;
   }

   public String getProviderCode() {
      return this.providerCode;
   }

   public String getInputModality() {
      return this.inputModality;
   }

   public String getOutputModality() {
      return this.outputModality;
   }

   public WorkflowParameters getParameters() {
      return this.parameters;
   }

   public Map<String, JsonNode> getUnknownFields() {
      return this.unknownFields;
   }

   public void setId(final String id) {
      this.id = id;
   }

   public void setKind(final String kind) {
      this.kind = kind;
   }

   public void setOutputModality(final String outputModality) {
      this.outputModality = outputModality;
   }
}

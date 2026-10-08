package com.auralink.creation.provider;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;

public record ProviderExecutionResult(String requestId, WorkflowOperation operation, String providerCode, WorkflowModality outputModality, ProviderOutput output) {
   public ProviderExecutionResult(String requestId, WorkflowOperation operation, String providerCode, WorkflowModality outputModality, ProviderOutput output) {
      if (requestId != null && operation != null && providerCode != null && outputModality != null && output != null) {
         this.requestId = requestId;
         this.operation = operation;
         this.providerCode = providerCode;
         this.outputModality = outputModality;
         this.output = output;
      } else {
         throw new IllegalArgumentException("Complete provider execution result is required");
      }
   }
}

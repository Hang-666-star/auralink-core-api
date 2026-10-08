package com.auralink.creation.provider;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;

public record ProviderAdapterBinding(WorkflowOperation operation, String providerCode, WorkflowModality inputModality, WorkflowModality outputModality) {
   public ProviderAdapterBinding(WorkflowOperation operation, String providerCode, WorkflowModality inputModality, WorkflowModality outputModality) {
      if (operation != null && providerCode != null && !providerCode.isBlank() && inputModality != null && outputModality != null) {
         this.operation = operation;
         this.providerCode = providerCode;
         this.inputModality = inputModality;
         this.outputModality = outputModality;
      } else {
         throw new IllegalArgumentException("Complete provider adapter binding is required");
      }
   }
}

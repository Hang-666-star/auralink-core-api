package com.auralink.workflow.capability;

import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import java.util.List;

public record WorkflowOperationCapability(
   WorkflowOperation operation,
   String displayName,
   WorkflowModality inputModality,
   WorkflowModality outputModality,
   boolean definitionEnabled,
   boolean draftEnabled,
   boolean executionAvailable,
   boolean terminalOutput,
   String availabilityReason,
   List<WorkflowProviderCapability> providers
) {
   public WorkflowOperationCapability {
      providers = List.copyOf(providers);
   }

   public boolean allowsProvider(String providerCode) {
      return this.providers.stream().anyMatch(provider -> provider.definitionEnabled() && provider.code().equals(providerCode));
   }

   public boolean allowsDraftProvider(String providerCode) {
      return this.providers.stream().anyMatch(provider -> provider.code().equals(providerCode));
   }
}

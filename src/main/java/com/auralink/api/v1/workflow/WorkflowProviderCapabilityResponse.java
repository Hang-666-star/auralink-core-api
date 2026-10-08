package com.auralink.api.v1.workflow;

import com.auralink.workflow.capability.WorkflowParameterSchema;
import com.auralink.workflow.capability.WorkflowProviderCapability;

public record WorkflowProviderCapabilityResponse(
   String code, String displayName, boolean definitionEnabled, boolean executionAvailable, WorkflowParameterSchema parameterSchema
) {
   static WorkflowProviderCapabilityResponse from(WorkflowProviderCapability provider, boolean executionAvailable) {
      return new WorkflowProviderCapabilityResponse(
         provider.code(), provider.displayName(), provider.definitionEnabled(), executionAvailable, provider.parameterSchema()
      );
   }
}

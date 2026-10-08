package com.auralink.workflow.capability;

public record WorkflowProviderCapability(
   String code, String displayName, boolean definitionEnabled, boolean executionAvailable, WorkflowParameterSchema parameterSchema
) {
}

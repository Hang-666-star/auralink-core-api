package com.auralink.api.v1.workflow;

import com.auralink.creation.CreationRuntimeCapabilityService;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.capability.WorkflowCapabilityRegistry;
import java.util.List;

public record WorkflowNodeTypesResponse(
   int workflowSchemaVersion,
   boolean featureEnabled,
   int maxVisibleCards,
   int maxTransformSteps,
   int maxExecutionTransformSteps,
   List<WorkflowModality> sourceModalities,
   List<WorkflowOperationCapabilityResponse> operations
) {
   public WorkflowNodeTypesResponse {
      sourceModalities = List.copyOf(sourceModalities);
      operations = List.copyOf(operations);
   }

   static WorkflowNodeTypesResponse from(
      int schemaVersion,
      boolean featureEnabled,
      int maxVisibleCards,
      int maxTransformSteps,
      int maxExecutionTransformSteps,
      WorkflowCapabilityRegistry registry,
      CreationRuntimeCapabilityService creationCapabilities
   ) {
      return new WorkflowNodeTypesResponse(
         schemaVersion,
         featureEnabled,
         maxVisibleCards,
         maxTransformSteps,
         maxExecutionTransformSteps,
         registry.sourceModalities(),
         registry.operations()
            .stream()
            .map(capability -> WorkflowOperationCapabilityResponse.from(capability, creationCapabilities.availability(capability)))
            .toList()
      );
   }
}

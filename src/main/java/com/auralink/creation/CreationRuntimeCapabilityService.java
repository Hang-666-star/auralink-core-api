package com.auralink.creation;

import com.auralink.config.properties.CreationExecutionProperties;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.capability.WorkflowOperationCapability;
import org.springframework.stereotype.Service;

@Service
public class CreationRuntimeCapabilityService {
   private final CreationExecutionProperties properties;
   private final CreationExecutionCapabilityService executionCapabilities;
   private final CreationRecoveryGate recoveryGate;

   public CreationExecutionCapabilityService.ExecutionAvailability availability(WorkflowOperationCapability capability) {
      WorkflowOperation operation = capability.operation();
      if (operation == WorkflowOperation.PAINTING_TO_VIDEO) {
         return new CreationExecutionCapabilityService.ExecutionAvailability(false, "RESERVED_FOR_FUTURE_IMPLEMENTATION");
      } else if (!this.properties.isEnabled()) {
         return new CreationExecutionCapabilityService.ExecutionAvailability(false, "CREATIONS_FEATURE_DISABLED");
      } else if (!this.recoveryGate.isOpen()) {
         return new CreationExecutionCapabilityService.ExecutionAvailability(false, "CREATION_RECOVERY_NOT_READY");
      } else {
         return capability.definitionEnabled() && capability.providers().size() == 1
            ? this.executionCapabilities.availability(operation, capability.providers().get(0).code())
            : new CreationExecutionCapabilityService.ExecutionAvailability(false, "CREATION_OPERATION_UNAVAILABLE");
      }
   }

   public CreationRuntimeCapabilityService(
      final CreationExecutionProperties properties, final CreationExecutionCapabilityService executionCapabilities, final CreationRecoveryGate recoveryGate
   ) {
      this.properties = properties;
      this.executionCapabilities = executionCapabilities;
      this.recoveryGate = recoveryGate;
   }
}

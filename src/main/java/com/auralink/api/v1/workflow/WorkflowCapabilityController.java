package com.auralink.api.v1.workflow;

import com.auralink.config.properties.CreationExecutionProperties;
import com.auralink.config.properties.WorkflowProperties;
import com.auralink.creation.CreationRuntimeCapabilityService;
import com.auralink.workflow.capability.WorkflowCapabilityRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowCapabilityController {
   private final WorkflowProperties properties;
   private final CreationExecutionProperties creationProperties;
   private final WorkflowCapabilityRegistry registry;
   private final CreationRuntimeCapabilityService creationCapabilities;

   @GetMapping("/node-types")
   public WorkflowNodeTypesResponse nodeTypes() {
      return WorkflowNodeTypesResponse.from(
         this.properties.getSchemaVersion(),
         this.properties.isEnabled(),
         3,
         2,
         this.creationProperties.getMaxExecutionTransformSteps(),
         this.registry,
         this.creationCapabilities
      );
   }

   public WorkflowCapabilityController(
      final WorkflowProperties properties,
      final CreationExecutionProperties creationProperties,
      final WorkflowCapabilityRegistry registry,
      final CreationRuntimeCapabilityService creationCapabilities
   ) {
      this.properties = properties;
      this.creationProperties = creationProperties;
      this.registry = registry;
      this.creationCapabilities = creationCapabilities;
   }
}

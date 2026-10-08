package com.auralink.workflow.capability;

import java.util.Map;

public record WorkflowParameterSchema(String type, Map<String, WorkflowParameterSchema.WorkflowParameterDefinition> properties, boolean additionalProperties) {
   public WorkflowParameterSchema {
      properties = Map.copyOf(properties);
   }

   public static WorkflowParameterSchema emptyStrictObject() {
      return new WorkflowParameterSchema("object", Map.of(), false);
   }

   public record WorkflowParameterDefinition(String type, Integer minimum, Integer maximum, Integer defaultValue) {
      public WorkflowParameterDefinition(String type, Integer minimum, Integer maximum, Integer defaultValue) {
         if (type != null && !type.isBlank()) {
            if (minimum != null && maximum != null && minimum > maximum) {
               throw new IllegalArgumentException("Parameter bounds are invalid");
            }

            if (defaultValue == null || (minimum == null || defaultValue >= minimum) && (maximum == null || defaultValue <= maximum)) {
               this.type = type;
               this.minimum = minimum;
               this.maximum = maximum;
               this.defaultValue = defaultValue;
            } else {
               throw new IllegalArgumentException("Parameter default is outside its bounds");
            }
         } else {
            throw new IllegalArgumentException("Parameter type is required");
         }
      }

      public static WorkflowParameterSchema.WorkflowParameterDefinition integer(int minimum, int maximum, int defaultValue) {
         return new WorkflowParameterSchema.WorkflowParameterDefinition("integer", minimum, maximum, defaultValue);
      }
   }
}

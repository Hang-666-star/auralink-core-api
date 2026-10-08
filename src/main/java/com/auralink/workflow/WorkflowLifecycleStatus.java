package com.auralink.workflow;

import java.util.Optional;

public enum WorkflowLifecycleStatus {
   DRAFT,
   ACTIVE;

   public static Optional<WorkflowLifecycleStatus> fromWire(String value) {
      if (value == null) {
         return Optional.empty();
      }

      try {
         return Optional.of(valueOf(value));
      } catch (IllegalArgumentException exception) {
         return Optional.empty();
      }
   }
}

package com.auralink.creation;

public interface CreationExecutionBoundaryHook {
   void reached(CreationExecutionBoundary boundary);

   default void artifactCloseAttempted() {
   }
}

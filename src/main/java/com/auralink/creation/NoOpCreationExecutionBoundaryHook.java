package com.auralink.creation;

final class NoOpCreationExecutionBoundaryHook implements CreationExecutionBoundaryHook {
   static final NoOpCreationExecutionBoundaryHook INSTANCE = new NoOpCreationExecutionBoundaryHook();

   private NoOpCreationExecutionBoundaryHook() {
   }

   @Override
   public void reached(CreationExecutionBoundary boundary) {
   }
}

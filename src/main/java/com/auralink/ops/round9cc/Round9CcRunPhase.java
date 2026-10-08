package com.auralink.ops.round9cc;

enum Round9CcRunPhase {
   INITIAL,
   SEED,
   RECOVERY;

   static Round9CcRunPhase require(String value) {
      try {
         return value == null ? INITIAL : valueOf(value);
      } catch (IllegalArgumentException exception) {
         throw Round9CcPackagedFailureHarness.manifestMismatch();
      }
   }
}

package com.auralink.ops.round81;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

final class Round81ProviderCallLedger {
   private final EnumMap<Round81ProviderFamily, Integer> counts = new EnumMap<>(Round81ProviderFamily.class);
   private boolean executionEntered;
   private Round81ProviderFamily lastProviderFamily;

   Round81ProviderCallLedger() {
      this.reset();
   }

   synchronized void reset() {
      for (Round81ProviderFamily family : Round81ProviderFamily.values()) {
         this.counts.put(family, 0);
      }

      this.executionEntered = false;
      this.lastProviderFamily = null;
   }

   synchronized void enterExecution() {
      if (this.executionEntered) {
         throw new Round81ValidationException("EXECUTION_COUNT_EXCEEDED", "Validation execution may be entered only once");
      }

      this.executionEntered = true;
   }

   synchronized void record(URI endpoint) {
      Round81ProviderFamily family = this.classify(endpoint);
      this.counts.put(family, Math.addExact(this.counts.get(family), 1));
      this.lastProviderFamily = family;
   }

   synchronized Map<String, Integer> safeCounts() {
      return Map.of(
         "seedream",
         this.counts.get(Round81ProviderFamily.SEEDREAM),
         "qwen",
         this.counts.get(Round81ProviderFamily.QWEN),
         "vmm",
         this.counts.get(Round81ProviderFamily.VMM)
      );
   }

   synchronized String safeLastProviderFamily() {
      return this.lastProviderFamily == null ? null : this.lastProviderFamily.name();
   }

   synchronized int totalCallCount() {
      return this.counts.values().stream().mapToInt(Integer::intValue).sum();
   }

   synchronized void requireExact(Round81ValidationOperation operation) {
      if (!this.executionEntered) {
         throw new Round81ValidationException("EXECUTION_COUNT_INVALID", "Provider execution was not entered");
      }

      for (Round81ProviderFamily family : Round81ProviderFamily.values()) {
         if (this.counts.get(family) != operation.expectedCalls(family)) {
            throw new Round81ValidationException("PROVIDER_CALL_COUNT_MISMATCH", "Provider invocation count did not match the reviewed budget");
         }
      }
   }

   private Round81ProviderFamily classify(URI endpoint) {
      if (endpoint != null && endpoint.getPath() != null) {
         String path = endpoint.getPath();
         if (path.endsWith("/images/generations")) {
            return Round81ProviderFamily.SEEDREAM;
         } else if (path.endsWith("/chat/completions")) {
            return Round81ProviderFamily.QWEN;
         } else if (path.endsWith("/api/generate_with_image")) {
            return Round81ProviderFamily.VMM;
         } else {
            throw new Round81ValidationException("PROVIDER_CALL_UNCLASSIFIED", "Provider invocation could not be classified");
         }
      } else {
         throw new Round81ValidationException("PROVIDER_CALL_UNCLASSIFIED", "Provider invocation could not be classified");
      }
   }
}

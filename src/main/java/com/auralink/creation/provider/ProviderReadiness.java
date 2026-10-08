package com.auralink.creation.provider;

public record ProviderReadiness(ProviderReadinessState state, String reason) {
   public ProviderReadiness(ProviderReadinessState state, String reason) {
      if (state != null && reason != null && !reason.isBlank()) {
         this.state = state;
         this.reason = reason;
      } else {
         throw new IllegalArgumentException("Provider readiness state and reason are required");
      }
   }

   public static ProviderReadiness implemented() {
      return new ProviderReadiness(ProviderReadinessState.ADAPTER_IMPLEMENTED, "PROVIDER_ADAPTER_IMPLEMENTED");
   }

   public static ProviderReadiness reservedDisabled() {
      return new ProviderReadiness(ProviderReadinessState.RESERVED_DISABLED, "RESERVED_FOR_FUTURE_IMPLEMENTATION");
   }
}

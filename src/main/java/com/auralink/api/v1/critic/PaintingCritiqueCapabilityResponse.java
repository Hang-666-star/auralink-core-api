package com.auralink.api.v1.critic;

public record PaintingCritiqueCapabilityResponse(boolean submissionAvailable, String reason) {
   public static PaintingCritiqueCapabilityResponse from(boolean submissionAvailable) {
      return new PaintingCritiqueCapabilityResponse(
         submissionAvailable, submissionAvailable ? "READY_FOR_CONTROLLED_EVALUATION" : "CRITIC_DISABLED_OR_UNCONFIGURED"
      );
   }
}

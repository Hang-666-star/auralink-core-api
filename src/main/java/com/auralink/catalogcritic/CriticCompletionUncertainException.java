package com.auralink.catalogcritic;

final class CriticCompletionUncertainException extends RuntimeException {
   CriticCompletionUncertainException() {
      super("Critic result persistence was intentionally interrupted");
   }
}

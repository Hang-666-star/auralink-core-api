package com.auralink.guide.provider;

public class GuideProviderException extends RuntimeException {
   private final GuideProviderException.Failure failure;
   private final boolean retryable;

   public GuideProviderException(GuideProviderException.Failure failure, boolean retryable, String safeMessage) {
      super(safeMessage);
      this.failure = failure;
      this.retryable = retryable;
   }

   public GuideProviderException(GuideProviderException.Failure failure, boolean retryable, String safeMessage, Throwable cause) {
      super(safeMessage, cause);
      this.failure = failure;
      this.retryable = retryable;
   }

   public GuideProviderException.Failure getFailure() {
      return this.failure;
   }

   public boolean isRetryable() {
      return this.retryable;
   }

   public enum Failure {
      CONFIGURATION,
      UNAVAILABLE,
      TIMEOUT,
      REJECTED,
      INVALID_RESPONSE;
   }
}

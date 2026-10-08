package com.auralink.critic.provider;

public class CriticProviderException extends RuntimeException {
   private final CriticProviderException.Failure failure;

   public CriticProviderException(CriticProviderException.Failure failure, String message, Throwable cause) {
      super(message, cause);
      this.failure = failure;
   }

   public CriticProviderException(CriticProviderException.Failure failure, String message) {
      this(failure, message, null);
   }

   public CriticProviderException.Failure failure() {
      return this.failure;
   }

   public enum Failure {
      UNAVAILABLE,
      TIMEOUT,
      INVALID_RESPONSE;
   }
}

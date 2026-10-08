package com.auralink.guide.model;

public class GuideResultValidationException extends RuntimeException {
   public GuideResultValidationException(String message) {
      super(message);
   }

   public GuideResultValidationException(String message, Throwable cause) {
      super(message, cause);
   }
}

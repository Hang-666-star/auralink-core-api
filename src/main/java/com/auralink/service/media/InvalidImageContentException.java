package com.auralink.service.media;

public class InvalidImageContentException extends RuntimeException {
   public InvalidImageContentException(String message) {
      super(message);
   }

   public InvalidImageContentException(String message, Throwable cause) {
      super(message, cause);
   }
}

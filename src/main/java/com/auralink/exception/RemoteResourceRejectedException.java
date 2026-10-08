package com.auralink.exception;

import java.io.IOException;

public class RemoteResourceRejectedException extends IOException {
   public RemoteResourceRejectedException(String message) {
      super(message);
   }

   public RemoteResourceRejectedException(String message, Throwable cause) {
      super(message, cause);
   }
}

package com.auralink.exception;

public class InvalidStoragePathException extends StorageException {
   public InvalidStoragePathException(String message) {
      super(message);
   }

   public InvalidStoragePathException(String message, Throwable cause) {
      super(message, cause);
   }
}

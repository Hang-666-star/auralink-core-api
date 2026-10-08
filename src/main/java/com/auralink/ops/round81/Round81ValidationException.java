package com.auralink.ops.round81;

public final class Round81ValidationException extends RuntimeException {
   private final String code;

   public Round81ValidationException(String code, String safeMessage) {
      super(safeMessage);
      this.code = code;
   }

   public Round81ValidationException(String code, String safeMessage, Throwable cause) {
      super(safeMessage, cause);
      this.code = code;
   }

   public String code() {
      return this.code;
   }
}

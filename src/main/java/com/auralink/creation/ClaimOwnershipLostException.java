package com.auralink.creation;

final class ClaimOwnershipLostException extends RuntimeException {
   ClaimOwnershipLostException() {
      super("Creation claim ownership is no longer current");
   }
}

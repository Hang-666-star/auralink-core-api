package com.auralink.ops.round9cc;

public final class Round9CcFailpointTimeoutException extends IllegalStateException {
   public Round9CcFailpointTimeoutException() {
      super("ROUND 9C-C failpoint timed out");
   }
}

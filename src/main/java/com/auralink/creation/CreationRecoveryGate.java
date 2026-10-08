package com.auralink.creation;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class CreationRecoveryGate {
   private final AtomicBoolean open = new AtomicBoolean(false);
   private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
   private int admittedProviderCalls;

   public boolean isOpen() {
      return this.open.get() && !this.shuttingDown.get();
   }

   public void openAfterRecovery() {
      if (!this.shuttingDown.get()) {
         this.open.set(true);
      }
   }

   public void close() {
      this.open.set(false);
   }

   public void beginShutdown() {
      this.shuttingDown.set(true);
      this.open.set(false);
   }

   public boolean isShuttingDown() {
      return this.shuttingDown.get();
   }

   public synchronized boolean tryBeginProviderCall() {
      if (!this.isOpen()) {
         return false;
      }

      this.admittedProviderCalls++;
      return true;
   }

   public synchronized void finishProviderCall() {
      if (this.admittedProviderCalls > 0) {
         this.admittedProviderCalls--;
      }
   }
}

package com.auralink.creation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "auralink.creations", name = "enabled", havingValue = "true")
public class CreationExecutionLifecycle implements SmartLifecycle {
   private final CreationRecoveryGate gate;
   private final CreationQueueDispatcher dispatcher;
   private final CreationRecoveryCoordinator recovery;
   private final ThreadPoolTaskExecutor workers;
   private final ThreadPoolTaskScheduler heartbeatScheduler;
   private final ThreadPoolTaskScheduler recoveryScheduler;
   private final CreationExecutionBoundaryHook boundaryHook;
   private volatile boolean running;

   public CreationExecutionLifecycle(
      CreationRecoveryGate gate,
      CreationQueueDispatcher dispatcher,
      CreationRecoveryCoordinator recovery,
      ThreadPoolTaskExecutor workers,
      ThreadPoolTaskScheduler heartbeatScheduler,
      ThreadPoolTaskScheduler recoveryScheduler
   ) {
      this(gate, dispatcher, recovery, workers, heartbeatScheduler, recoveryScheduler, NoOpCreationExecutionBoundaryHook.INSTANCE);
   }

   @Autowired
   public CreationExecutionLifecycle(
      CreationRecoveryGate gate,
      CreationQueueDispatcher dispatcher,
      CreationRecoveryCoordinator recovery,
      @Qualifier("creationWorkerExecutor") ThreadPoolTaskExecutor workers,
      @Qualifier("creationHeartbeatScheduler") ThreadPoolTaskScheduler heartbeatScheduler,
      @Qualifier("creationRecoveryScheduler") ThreadPoolTaskScheduler recoveryScheduler,
      CreationExecutionBoundaryHook boundaryHook
   ) {
      this.gate = gate;
      this.dispatcher = dispatcher;
      this.recovery = recovery;
      this.workers = workers;
      this.heartbeatScheduler = heartbeatScheduler;
      this.recoveryScheduler = recoveryScheduler;
      this.boundaryHook = boundaryHook;
   }

   public void start() {
      this.running = true;
   }

   public void stop() {
      if (this.running) {
         this.gate.beginShutdown();
         this.boundaryHook.reached(CreationExecutionBoundary.GRACEFUL_SHUTDOWN_DURING_AWAIT);
         this.dispatcher.stopDispatching();
         this.recovery.stopRecovery();
         this.workers.shutdown();
         this.heartbeatScheduler.shutdown();
         this.recoveryScheduler.shutdown();
         this.running = false;
      }
   }

   public void stop(Runnable callback) {
      this.stop();
      callback.run();
   }

   public boolean isRunning() {
      return this.running;
   }

   public boolean isAutoStartup() {
      return true;
   }

   public int getPhase() {
      return Integer.MAX_VALUE;
   }
}

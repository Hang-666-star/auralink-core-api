package com.auralink.creation;

import com.auralink.config.properties.CreationExecutionProperties;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "auralink.creations", name = "enabled", havingValue = "true")
public class CreationRecoveryCoordinator {
   private static final Logger log = LoggerFactory.getLogger(CreationRecoveryCoordinator.class);
   private final CreationRecoveryTransactionService transactions;
   private final CreationRecoveryGate gate;
   private final CreationExecutionProperties properties;
   private final TaskScheduler scheduler;
   private final CreationExecutionBoundaryHook boundaryHook;
   private final AtomicBoolean sweeping = new AtomicBoolean(false);
   private final AtomicBoolean stopped = new AtomicBoolean(false);
   private final AtomicBoolean startupInitialized = new AtomicBoolean(false);
   private volatile ScheduledFuture<?> periodicTask;

   public CreationRecoveryCoordinator(
      CreationRecoveryTransactionService transactions, CreationRecoveryGate gate, CreationExecutionProperties properties, TaskScheduler scheduler
   ) {
      this(transactions, gate, properties, scheduler, NoOpCreationExecutionBoundaryHook.INSTANCE);
   }

   @Autowired
   public CreationRecoveryCoordinator(
      CreationRecoveryTransactionService transactions,
      CreationRecoveryGate gate,
      CreationExecutionProperties properties,
      @Qualifier("creationRecoveryScheduler") TaskScheduler scheduler,
      CreationExecutionBoundaryHook boundaryHook
   ) {
      this.transactions = transactions;
      this.gate = gate;
      this.properties = properties;
      this.scheduler = scheduler;
      this.boundaryHook = boundaryHook;
   }

   @EventListener(ApplicationReadyEvent.class)
   public void onApplicationReady() {
      this.runStartupRecovery();
      this.startupInitialized.set(true);
      if (!this.stopped.get()) {
         this.periodicTask = this.scheduler.scheduleWithFixedDelay(this::scheduledRecovery, this.properties.getRecoveryInterval());
      }
   }

   public void scheduledRecovery() {
      if (!this.stopped.get() && !this.gate.isShuttingDown() && this.startupInitialized.get()) {
         if (this.sweeping.compareAndSet(false, true)) {
            try {
               this.boundaryHook.reached(CreationExecutionBoundary.SCHEDULED_RECOVERY_SWEEP);
               if (!this.gate.isOpen()) {
                  this.runStartupRecovery();
               } else {
                  this.recoverOneBatch();
               }
            } finally {
               this.sweeping.set(false);
            }
         }
      }
   }

   public void runStartupRecovery() {
      if (!this.stopped.get() && !this.gate.isShuttingDown()) {
         this.gate.close();
         this.boundaryHook.reached(CreationExecutionBoundary.STARTUP_RECOVERY_GATE_CLOSED);

         try {
            for (int batch = 0; batch < this.properties.getStartupMaxBatches(); batch++) {
               List<CreationRecoveryTransactionService.RecoveryCandidate> candidates = this.transactions.candidates();
               if (candidates.isEmpty()) {
                  this.gate.openAfterRecovery();
                  return;
               }

               this.recover(candidates);
            }

            this.gate.close();
         } catch (RuntimeException exception) {
            this.gate.close();
            log.warn("Creation startup recovery did not complete; dispatcher remains gated");
         }
      }
   }

   public void recoverOneBatch() {
      if (!this.stopped.get() && !this.gate.isShuttingDown()) {
         try {
            this.recover(this.transactions.candidates());
         } catch (RuntimeException exception) {
            log.warn("Creation periodic recovery sweep did not complete");
         }
      }
   }

   private void recover(List<CreationRecoveryTransactionService.RecoveryCandidate> candidates) {
      for (CreationRecoveryTransactionService.RecoveryCandidate candidate : candidates) {
         Optional<CreationRecoveryTransactionService.RecoveryFence> fence = Optional.empty();

         try {
            fence = this.transactions.fence(candidate);
            fence.flatMap(this.transactions::inspect).ifPresent(this.transactions::apply);
         } catch (RuntimeException exception) {
            fence.ifPresent(this.transactions::quarantineUnexpected);
            log.warn("Creation recovery item did not complete and requires later operator review");
         }
      }
   }

   public void stopRecovery() {
      this.stopped.set(true);
      ScheduledFuture<?> scheduled = this.periodicTask;
      if (scheduled != null) {
         scheduled.cancel(false);
      }
   }

   boolean isStartupInitialized() {
      return this.startupInitialized.get();
   }
}

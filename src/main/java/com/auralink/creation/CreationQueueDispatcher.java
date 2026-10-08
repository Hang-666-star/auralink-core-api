package com.auralink.creation;

import com.auralink.config.properties.CreationExecutionProperties;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "auralink.creations", name = "enabled", havingValue = "true")
public class CreationQueueDispatcher {
   private final CreationExecutionTransactionService transactions;
   private final CreationWorker worker;
   private final CreationExecutionProperties properties;
   private final CreationRecoveryGate recoveryGate;
   private final ThreadPoolTaskExecutor executor;
   private final CreationExecutionBoundaryHook boundaryHook;
   private final AtomicBoolean acceptingDispatches = new AtomicBoolean(true);

   @Autowired
   public CreationQueueDispatcher(
      CreationExecutionTransactionService transactions,
      CreationWorker worker,
      CreationExecutionProperties properties,
      CreationRecoveryGate recoveryGate,
      @Qualifier("creationWorkerExecutor") ThreadPoolTaskExecutor executor,
      CreationExecutionBoundaryHook boundaryHook
   ) {
      this.transactions = transactions;
      this.worker = worker;
      this.properties = properties;
      this.recoveryGate = recoveryGate;
      this.executor = executor;
      this.boundaryHook = boundaryHook;
   }

   public CreationQueueDispatcher(
      CreationExecutionTransactionService transactions,
      CreationWorker worker,
      CreationExecutionProperties properties,
      CreationRecoveryGate recoveryGate,
      ThreadPoolTaskExecutor executor
   ) {
      this(transactions, worker, properties, recoveryGate, executor, NoOpCreationExecutionBoundaryHook.INSTANCE);
   }

   @Scheduled(fixedDelayString = "${auralink.creations.dispatch-delay:1000}", initialDelayString = "${auralink.creations.dispatch-delay:1000}")
   public void dispatchOne() {
      if (this.acceptingDispatches.get() && this.properties.isEnabled() && this.recoveryGate.isOpen()) {
         Optional<CreationExecutionTransactionService.ClaimedCreation> claim = this.transactions.claimOldestQueued();
         if (!claim.isEmpty()) {
            this.boundaryHook.reached(CreationExecutionBoundary.CLAIM_COMMITTED_BEFORE_SUBMIT);
            if (this.acceptingDispatches.get() && this.recoveryGate.isOpen()) {
               try {
                  this.executor.execute(() -> this.worker.execute(claim.get()));
               } catch (TaskRejectedException exception) {
                  this.transactions.returnRejectedSubmissionToQueue(claim.get().id(), claim.get().claimToken());
               }
            } else {
               this.transactions.returnRejectedSubmissionToQueue(claim.get().id(), claim.get().claimToken());
            }
         }
      }
   }

   void stopDispatching() {
      this.acceptingDispatches.set(false);
   }
}

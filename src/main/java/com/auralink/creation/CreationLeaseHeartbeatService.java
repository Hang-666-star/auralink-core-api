package com.auralink.creation;

import com.auralink.config.properties.CreationExecutionProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "auralink.creations", name = "enabled", havingValue = "true")
public class CreationLeaseHeartbeatService {
   private static final Logger log = LoggerFactory.getLogger(CreationLeaseHeartbeatService.class);
   private final CreationLeaseHeartbeatTransactionService transactions;
   private final CreationExecutionProperties properties;
   private final Clock clock;
   private final TaskScheduler scheduler;

   public CreationLeaseHeartbeatService(
      CreationLeaseHeartbeatTransactionService transactions,
      CreationExecutionProperties properties,
      Clock clock,
      @Qualifier("creationHeartbeatScheduler") TaskScheduler scheduler
   ) {
      this.transactions = transactions;
      this.properties = properties;
      this.clock = clock;
      this.scheduler = scheduler;
   }

   public CreationLeaseHeartbeatService.LeaseHeartbeatHandle start(Long creationId, String claimToken) {
      CreationLeaseHeartbeatService.LeaseHeartbeatHandle handle = new CreationLeaseHeartbeatService.LeaseHeartbeatHandle(creationId, claimToken);
      ScheduledFuture<?> future = this.scheduler.scheduleAtFixedRate(() -> this.heartbeat(handle), this.properties.getHeartbeatInterval());
      handle.setFuture(future);
      return handle;
   }

   void heartbeat(CreationLeaseHeartbeatService.LeaseHeartbeatHandle handle) {
      if (!handle.closed.get() && !handle.ownershipLost.get()) {
         try {
            LocalDateTime now = LocalDateTime.now(this.clock);
            int updated = this.transactions.refresh(handle.creationId, handle.claimToken, now.plus(this.properties.getLeaseDuration()), now);
            if (updated == 0) {
               handle.ownershipLost.set(true);
               handle.close();
            }
         } catch (RuntimeException exception) {
            log.warn("Creation lease heartbeat did not complete; a later bounded heartbeat will retry");
         }
      }
   }

   public static final class LeaseHeartbeatHandle implements AutoCloseable {
      private final Long creationId;
      private final String claimToken;
      private final AtomicBoolean ownershipLost = new AtomicBoolean(false);
      private final AtomicBoolean closed = new AtomicBoolean(false);
      private volatile ScheduledFuture<?> future;

      private LeaseHeartbeatHandle(Long creationId, String claimToken) {
         this.creationId = creationId;
         this.claimToken = claimToken;
      }

      private void setFuture(ScheduledFuture<?> future) {
         this.future = future;
         if (this.closed.get() && future != null) {
            future.cancel(false);
         }
      }

      public boolean ownershipLost() {
         return this.ownershipLost.get();
      }

      @Override
      public void close() {
         if (this.closed.compareAndSet(false, true)) {
            ScheduledFuture<?> scheduled = this.future;
            if (scheduled != null) {
               scheduled.cancel(false);
            }
         }
      }
   }
}

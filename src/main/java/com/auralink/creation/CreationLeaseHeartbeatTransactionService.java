package com.auralink.creation;

import com.auralink.repository.CreationRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreationLeaseHeartbeatTransactionService {
   private final CreationRepository creations;

   public CreationLeaseHeartbeatTransactionService(CreationRepository creations) {
      this.creations = creations;
   }

   @Transactional
   public int refresh(Long creationId, String claimToken, LocalDateTime leaseExpiresAt, LocalDateTime now) {
      return this.creations.refreshLease(creationId, claimToken, leaseExpiresAt, now);
   }
}

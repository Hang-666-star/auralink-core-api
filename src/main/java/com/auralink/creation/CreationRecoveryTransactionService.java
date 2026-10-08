package com.auralink.creation;

import com.auralink.config.properties.CreationExecutionProperties;
import com.auralink.entity.Creation;
import com.auralink.entity.CreationExecutionAttempt;
import com.auralink.entity.CreationStep;
import com.auralink.entity.CreationStepDispatchAttempt;
import com.auralink.repository.CreationExecutionAttemptRepository;
import com.auralink.repository.CreationRepository;
import com.auralink.repository.CreationStepDispatchAttemptRepository;
import com.auralink.repository.CreationStepRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreationRecoveryTransactionService {
   private final CreationRepository creations;
   private final CreationStepRepository steps;
   private final CreationExecutionAttemptRepository executionAttempts;
   private final CreationStepDispatchAttemptRepository dispatchAttempts;
   private final CreationExecutionProperties properties;
   private final CreationRecoveryStateInspector inspector;
   private final Clock clock;

   public CreationRecoveryTransactionService(
      CreationRepository creations,
      CreationStepRepository steps,
      CreationExecutionAttemptRepository executionAttempts,
      CreationStepDispatchAttemptRepository dispatchAttempts,
      CreationExecutionProperties properties,
      CreationRecoveryStateInspector inspector,
      Clock clock
   ) {
      this.creations = creations;
      this.steps = steps;
      this.executionAttempts = executionAttempts;
      this.dispatchAttempts = dispatchAttempts;
      this.properties = properties;
      this.inspector = inspector;
      this.clock = clock;
   }

   @Transactional(readOnly = true)
   public List<CreationRecoveryTransactionService.RecoveryCandidate> candidates() {
      LocalDateTime cutoff = LocalDateTime.now(this.clock).minus(this.properties.getRecoveryGrace());
      return this.creations
         .findExpiredRecoveryCandidates(cutoff, this.properties.getRecoveryBatchSize())
         .stream()
         .map(creation -> new CreationRecoveryTransactionService.RecoveryCandidate(creation.getId(), creation.getClaimToken(), creation.getLeaseExpiresAt()))
         .toList();
   }

   @Transactional
   public Optional<CreationRecoveryTransactionService.RecoveryFence> fence(CreationRecoveryTransactionService.RecoveryCandidate candidate) {
      if (candidate != null && candidate.claimToken() != null && candidate.leaseExpiresAt() != null) {
         LocalDateTime now = LocalDateTime.now(this.clock);
         LocalDateTime cutoff = now.minus(this.properties.getRecoveryGrace());
         String token = UUID.randomUUID().toString();
         int changed = this.creations
            .fenceExpiredClaim(
               candidate.creationId(),
               candidate.claimToken(),
               candidate.leaseExpiresAt(),
               cutoff,
               token,
               now.plus(this.properties.getRecoveryFenceLease()),
               now
            );
         return changed == 1 ? Optional.of(new CreationRecoveryTransactionService.RecoveryFence(candidate.creationId(), token)) : Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   @Transactional(readOnly = true)
   public Optional<CreationRecoveryTransactionService.FencedInspection> inspect(CreationRecoveryTransactionService.RecoveryFence fence) {
      Optional<Creation> creation = this.creations.findByIdAndStatusAndClaimToken(fence.creationId(), CreationStatus.RUNNING.name(), fence.recoveryToken());
      if (creation.isEmpty()) {
         return Optional.empty();
      }

      List<CreationStep> creationSteps = this.steps.findByCreationIdOrderByStepIndexAsc(fence.creationId());
      List<CreationExecutionAttempt> active = this.executionAttempts.findByCreationIdAndFinishedAtIsNullOrderByIdAsc(fence.creationId());
      HashMap<Long, CreationStepDispatchAttempt> perStep = new HashMap<>();
      if (active.size() == 1) {
         for (CreationStep step : creationSteps) {
            this.dispatchAttempts
               .findByCreationStepIdAndCreationExecutionAttemptId(step.getId(), active.get(0).getId())
               .ifPresent(attempt -> perStep.put(step.getId(), attempt));
         }
      }

      CreationRecoveryStateInspector.RecoveryDecision decision = this.inspector.inspect(creationSteps, active, perStep);
      return Optional.of(new CreationRecoveryTransactionService.FencedInspection(fence, active.size() == 1 ? active.get(0).getId() : null, decision));
   }

   @Transactional
   public boolean apply(CreationRecoveryTransactionService.FencedInspection inspection) {
      if (inspection == null) {
         return false;
      }

      CreationRecoveryTransactionService.RecoveryFence fence = inspection.fence();
      CreationRecoveryStateInspector.RecoveryDecision decision = inspection.decision();
      LocalDateTime now = LocalDateTime.now(this.clock);

      return switch (decision.kind()) {
         case REQUEUE -> this.creations.requeueRecovered(fence.creationId(), fence.recoveryToken(), now) == 1;
         case REQUEUE_NOT_SENT -> this.requeueNotSent(inspection, now);
         case FINALIZE_SUCCESS -> this.finalizeSuccess(inspection, now);
         case FINALIZE_FAILED -> this.finalizeFailed(inspection, now, false);
         case AMBIGUOUS -> this.finalizeFailed(inspection, now, true);
         case INCONSISTENT -> this.quarantine(inspection, now);
      };
   }

   @Transactional
   public boolean quarantineUnexpected(CreationRecoveryTransactionService.RecoveryFence fence) {
      if (fence == null) {
         return false;
      }

      List<CreationExecutionAttempt> active = this.executionAttempts.findByCreationIdAndFinishedAtIsNullOrderByIdAsc(fence.creationId());
      List<CreationStep> creationSteps = this.steps.findByCreationIdOrderByStepIndexAsc(fence.creationId());
      boolean priorSuccess = creationSteps.stream().anyMatch(step -> CreationStepStatus.SUCCEEDED.name().equals(step.getStatus()));
      LocalDateTime now = LocalDateTime.now(this.clock);
      String status = priorSuccess ? CreationStatus.PARTIAL_SUCCESS.name() : CreationStatus.FAILED.name();
      return this.creations
               .terminalizeRecovered(fence.creationId(), fence.recoveryToken(), status, "CREATION_STATE_INCONSISTENT", "CREATION_STATE_INCONSISTENT", now)
            != 1
         ? false
         : active.size() != 1 || this.finishAttempt(active.get(0).getId(), "CREATION_STATE_INCONSISTENT", now);
   }

   private boolean requeueNotSent(CreationRecoveryTransactionService.FencedInspection inspection, LocalDateTime now) {
      if (inspection.executionAttemptId() != null && inspection.decision().boundary() != null) {
         CreationStep step = inspection.decision().boundary();
         CreationRecoveryTransactionService.RecoveryFence fence = inspection.fence();
         if (this.dispatchAttempts.markRecoveryRequeuedNotSent(step.getId(), inspection.executionAttemptId(), fence.creationId(), fence.recoveryToken(), now)
            != 1) {
            return false;
         } else {
            return this.steps.resetRecoveredNotSent(step.getId(), fence.creationId(), fence.recoveryToken()) != 1
               ? false
               : this.creations.requeueRecovered(fence.creationId(), fence.recoveryToken(), now) == 1;
         }
      } else {
         return this.quarantine(inspection, now);
      }
   }

   private boolean finalizeSuccess(CreationRecoveryTransactionService.FencedInspection inspection, LocalDateTime now) {
      if (inspection.executionAttemptId() == null) {
         return this.quarantine(inspection, now);
      }

      CreationRecoveryStateInspector.RecoveryDecision decision = inspection.decision();
      CreationRecoveryTransactionService.RecoveryFence fence = inspection.fence();
      return this.creations
               .finalizeRecoveredSuccess(
                  fence.creationId(), fence.recoveryToken(), decision.finalModality(), decision.finalAssetId(), decision.finalOutputJson(), now
               )
            != 1
         ? false
         : this.finishAttempt(inspection.executionAttemptId(), "RECOVERY_FINALIZED_FROM_PERSISTED_RESULT", now);
   }

   private boolean finalizeFailed(CreationRecoveryTransactionService.FencedInspection inspection, LocalDateTime now, boolean markRunningStepFailed) {
      if (inspection.executionAttemptId() == null) {
         return this.quarantine(inspection, now);
      }

      CreationRecoveryStateInspector.RecoveryDecision decision = inspection.decision();
      CreationRecoveryTransactionService.RecoveryFence fence = inspection.fence();
      if (markRunningStepFailed) {
         CreationStep step = decision.boundary();
         if (step == null
            || this.steps.failRecoveredRunning(step.getId(), fence.creationId(), fence.recoveryToken(), decision.errorCode(), decision.errorCode(), now) != 1) {
            return false;
         }

         if (this.dispatchAttempts
               .finishFailure(step.getId(), inspection.executionAttemptId(), fence.creationId(), fence.recoveryToken(), decision.errorCode(), now)
            != 1) {
            return false;
         }
      }

      String status = decision.priorSuccess() ? CreationStatus.PARTIAL_SUCCESS.name() : CreationStatus.FAILED.name();
      return this.creations.terminalizeRecovered(fence.creationId(), fence.recoveryToken(), status, decision.errorCode(), decision.errorCode(), now) != 1
         ? false
         : this.finishAttempt(inspection.executionAttemptId(), decision.errorCode(), now);
   }

   private boolean quarantine(CreationRecoveryTransactionService.FencedInspection inspection, LocalDateTime now) {
      CreationRecoveryTransactionService.RecoveryFence fence = inspection.fence();
      CreationRecoveryStateInspector.RecoveryDecision decision = inspection.decision();
      String code = decision.errorCode() == null ? "CREATION_STATE_INCONSISTENT" : decision.errorCode();
      String status = decision.priorSuccess() ? CreationStatus.PARTIAL_SUCCESS.name() : CreationStatus.FAILED.name();
      return this.creations.terminalizeRecovered(fence.creationId(), fence.recoveryToken(), status, code, code, now) != 1
         ? false
         : inspection.executionAttemptId() == null || this.finishAttempt(inspection.executionAttemptId(), code, now);
   }

   private boolean finishAttempt(Long attemptId, String resolutionCode, LocalDateTime now) {
      CreationExecutionAttempt attempt = (CreationExecutionAttempt)this.executionAttempts.findById(attemptId).orElse(null);
      if (attempt != null && attempt.getFinishedAt() == null) {
         attempt.setFinishedAt(now);
         attempt.setResolutionCode(resolutionCode);
         this.executionAttempts.save(attempt);
         return true;
      } else {
         return false;
      }
   }

   public record FencedInspection(
      CreationRecoveryTransactionService.RecoveryFence fence, Long executionAttemptId, CreationRecoveryStateInspector.RecoveryDecision decision
   ) {
   }

   public record RecoveryCandidate(Long creationId, String claimToken, LocalDateTime leaseExpiresAt) {
   }

   public record RecoveryFence(Long creationId, String recoveryToken) {
   }
}

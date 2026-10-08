package com.auralink.repository;

import com.auralink.entity.CreationStepDispatchAttempt;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreationStepDispatchAttemptRepository extends JpaRepository<CreationStepDispatchAttempt, Long> {
   List<CreationStepDispatchAttempt> findByCreationStepIdOrderByIdAsc(Long creationStepId);

   Optional<CreationStepDispatchAttempt> findByCreationStepIdAndCreationExecutionAttemptId(Long creationStepId, Long creationExecutionAttemptId);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_step_dispatch_attempts\nSET finished_at = :now, resolution_code = 'RECOVERY_REQUEUED_NOT_SENT'\nWHERE creation_step_id = :stepId\n  AND creation_execution_attempt_id = :executionAttemptId\n  AND dispatch_state = 'NOT_SENT'\n  AND provider_request_key IS NULL\n  AND EXISTS (SELECT 1 FROM creations c WHERE c.id = :creationId\n              AND c.status = 'RUNNING' AND c.claim_token = :recoveryToken)\n",
      nativeQuery = true
   )
   int markRecoveryRequeuedNotSent(
      @Param("stepId") Long stepId,
      @Param("executionAttemptId") Long executionAttemptId,
      @Param("creationId") Long creationId,
      @Param("recoveryToken") String recoveryToken,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_step_dispatch_attempts\nSET provider_request_key = :requestKey, dispatch_state = 'SEND_STARTED',\n    dispatch_started_at = :now, finished_at = NULL,\n    resolution_code = CASE WHEN resolution_code = 'RECOVERY_REQUEUED_NOT_SENT'\n        THEN 'RECOVERY_REQUEUED_NOT_SENT' ELSE NULL END\nWHERE creation_step_id = :stepId\n  AND creation_execution_attempt_id = :executionAttemptId\n  AND dispatch_state = 'NOT_SENT'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int markSendStarted(
      @Param("stepId") Long stepId,
      @Param("executionAttemptId") Long executionAttemptId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("requestKey") String requestKey,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_step_dispatch_attempts\nSET dispatch_state = 'RESULT_PERSISTED', finished_at = :now,\n    resolution_code = CASE WHEN resolution_code = 'RECOVERY_REQUEUED_NOT_SENT'\n        THEN 'RECOVERY_REQUEUED_NOT_SENT;RESULT_PERSISTED' ELSE 'RESULT_PERSISTED' END,\n    result_asset_id = :assetId,\n    result_digest = :resultDigest, canonical_poem_digest = NULL\nWHERE creation_step_id = :stepId\n  AND creation_execution_attempt_id = :executionAttemptId\n  AND dispatch_state = 'SEND_STARTED'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int persistPaintingResult(
      @Param("stepId") Long stepId,
      @Param("executionAttemptId") Long executionAttemptId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("assetId") Long assetId,
      @Param("resultDigest") String resultDigest,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_step_dispatch_attempts\nSET dispatch_state = 'RESULT_PERSISTED', finished_at = :now,\n    resolution_code = CASE WHEN resolution_code = 'RECOVERY_REQUEUED_NOT_SENT'\n        THEN 'RECOVERY_REQUEUED_NOT_SENT;RESULT_PERSISTED' ELSE 'RESULT_PERSISTED' END,\n    result_asset_id = NULL,\n    result_digest = NULL, canonical_poem_digest = :poemDigest\nWHERE creation_step_id = :stepId\n  AND creation_execution_attempt_id = :executionAttemptId\n  AND dispatch_state = 'SEND_STARTED'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int persistPoemResult(
      @Param("stepId") Long stepId,
      @Param("executionAttemptId") Long executionAttemptId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("poemDigest") String poemDigest,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_step_dispatch_attempts\nSET dispatch_state = 'RESULT_PERSISTED', finished_at = :now,\n    resolution_code = CASE WHEN resolution_code = 'RECOVERY_REQUEUED_NOT_SENT'\n        THEN 'RECOVERY_REQUEUED_NOT_SENT;RESULT_PERSISTED' ELSE 'RESULT_PERSISTED' END,\n    result_asset_id = :assetId,\n    result_digest = :resultDigest, canonical_poem_digest = NULL\nWHERE creation_step_id = :stepId\n  AND creation_execution_attempt_id = :executionAttemptId\n  AND dispatch_state = 'SEND_STARTED'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int persistMusicResult(
      @Param("stepId") Long stepId,
      @Param("executionAttemptId") Long executionAttemptId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("assetId") Long assetId,
      @Param("resultDigest") String resultDigest,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_step_dispatch_attempts\nSET finished_at = :now, resolution_code = :resolutionCode\nWHERE creation_step_id = :stepId\n  AND creation_execution_attempt_id = :executionAttemptId\n  AND finished_at IS NULL\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int finishFailure(
      @Param("stepId") Long stepId,
      @Param("executionAttemptId") Long executionAttemptId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("resolutionCode") String resolutionCode,
      @Param("now") LocalDateTime now
   );
}

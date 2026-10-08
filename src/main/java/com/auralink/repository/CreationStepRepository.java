package com.auralink.repository;

import com.auralink.entity.CreationStep;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreationStepRepository extends JpaRepository<CreationStep, Long> {
   Optional<CreationStep> findByPublicId(String publicId);

   boolean existsByPublicId(String publicId);

   Optional<CreationStep> findByCreationIdAndStepIndex(Long creationId, int stepIndex);

   boolean existsByCreationIdAndStepIndex(Long creationId, int stepIndex);

   List<CreationStep> findByCreationIdOrderByStepIndexAsc(Long creationId);

   Optional<CreationStep> findFirstByCreationIdAndStatusOrderByStepIndexAsc(Long creationId, String status);

   long countByCreationIdAndStatus(Long creationId, String status);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'RUNNING', attempt_count = attempt_count + 1, started_at = :now,\n    finished_at = NULL, provider_dispatch_state = 'NOT_SENT', provider_request_key = NULL,\n    error_code = NULL, error_message = NULL\nWHERE id = :stepId AND creation_id = :creationId AND status = 'PENDING'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int startPending(@Param("stepId") Long stepId, @Param("creationId") Long creationId, @Param("claimToken") String claimToken, @Param("now") LocalDateTime now);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET provider_dispatch_state = 'SEND_STARTED', provider_request_key = :requestKey\nWHERE id = :stepId AND creation_id = :creationId AND status = 'RUNNING'\n  AND provider_dispatch_state = 'NOT_SENT'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int markSendStarted(
      @Param("stepId") Long stepId, @Param("creationId") Long creationId, @Param("claimToken") String claimToken, @Param("requestKey") String requestKey
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'SUCCEEDED', finished_at = :now, provider_dispatch_state = 'RESULT_PERSISTED',\n    output_asset_id = :assetId, output_json = NULL, error_code = NULL, error_message = NULL\nWHERE id = :stepId AND creation_id = :creationId AND status = 'RUNNING'\n  AND provider_dispatch_state = 'SEND_STARTED'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int persistImageSuccess(
      @Param("stepId") Long stepId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("assetId") Long assetId,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'SUCCEEDED', finished_at = :now, provider_dispatch_state = 'RESULT_PERSISTED',\n    output_asset_id = NULL, output_json = :outputJson, error_code = NULL, error_message = NULL\nWHERE id = :stepId AND creation_id = :creationId AND status = 'RUNNING'\n  AND provider_dispatch_state = 'SEND_STARTED'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int persistPoemSuccess(
      @Param("stepId") Long stepId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("outputJson") String outputJson,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'SUCCEEDED', finished_at = :now, provider_dispatch_state = 'RESULT_PERSISTED',\n    output_asset_id = :assetId, output_json = NULL, error_code = NULL, error_message = NULL\nWHERE id = :stepId AND creation_id = :creationId AND status = 'RUNNING'\n  AND provider_dispatch_state = 'SEND_STARTED'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int persistMusicSuccess(
      @Param("stepId") Long stepId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("assetId") Long assetId,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'FAILED', finished_at = :now, error_code = :errorCode, error_message = :errorMessage\nWHERE id = :stepId AND creation_id = :creationId AND status = 'RUNNING'\n  AND EXISTS (\n      SELECT 1 FROM creations c WHERE c.id = :creationId\n        AND c.status = 'RUNNING' AND c.claim_token = :claimToken\n  )\n",
      nativeQuery = true
   )
   int failRunning(
      @Param("stepId") Long stepId,
      @Param("creationId") Long creationId,
      @Param("claimToken") String claimToken,
      @Param("errorCode") String errorCode,
      @Param("errorMessage") String errorMessage,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'PENDING', started_at = NULL, finished_at = NULL,\n    provider_dispatch_state = 'NOT_SENT', provider_request_key = NULL,\n    error_code = NULL, error_message = NULL, output_asset_id = NULL, output_json = NULL\nWHERE id = :stepId AND creation_id = :creationId\n  AND status = 'RUNNING' AND provider_dispatch_state = 'NOT_SENT'\n  AND EXISTS (SELECT 1 FROM creations c WHERE c.id = :creationId\n              AND c.status = 'RUNNING' AND c.claim_token = :recoveryToken)\n",
      nativeQuery = true
   )
   int resetRecoveredNotSent(@Param("stepId") Long stepId, @Param("creationId") Long creationId, @Param("recoveryToken") String recoveryToken);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'FAILED', finished_at = :now, error_code = :errorCode, error_message = :errorMessage\nWHERE id = :stepId AND creation_id = :creationId AND status = 'RUNNING'\n  AND EXISTS (SELECT 1 FROM creations c WHERE c.id = :creationId\n              AND c.status = 'RUNNING' AND c.claim_token = :recoveryToken)\n",
      nativeQuery = true
   )
   int failRecoveredRunning(
      @Param("stepId") Long stepId,
      @Param("creationId") Long creationId,
      @Param("recoveryToken") String recoveryToken,
      @Param("errorCode") String errorCode,
      @Param("errorMessage") String errorMessage,
      @Param("now") LocalDateTime now
   );

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE creation_steps\nSET status = 'PENDING', finished_at = NULL, started_at = NULL,\n    provider_dispatch_state = 'NOT_SENT', provider_request_key = NULL,\n    error_code = NULL, error_message = NULL,\n    output_asset_id = NULL, output_json = NULL\nWHERE creation_id = :creationId AND step_index >= :boundaryIndex\n  AND status IN ('PENDING', 'FAILED')\n",
      nativeQuery = true
   )
   int resetForSafeRetry(@Param("creationId") Long creationId, @Param("boundaryIndex") int boundaryIndex);
}

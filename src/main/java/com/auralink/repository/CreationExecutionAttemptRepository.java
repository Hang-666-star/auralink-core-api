package com.auralink.repository;

import com.auralink.entity.CreationExecutionAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreationExecutionAttemptRepository extends JpaRepository<CreationExecutionAttempt, Long> {
   Optional<CreationExecutionAttempt> findByCreationIdAndFinishedAtIsNull(Long creationId);

   List<CreationExecutionAttempt> findByCreationIdAndFinishedAtIsNullOrderByIdAsc(Long creationId);

   Optional<CreationExecutionAttempt> findByCreationIdAndRetryIdempotencyKeyDigest(Long creationId, String retryIdempotencyKeyDigest);

   long countByCreationId(Long creationId);
}

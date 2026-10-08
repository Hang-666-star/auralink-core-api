package com.auralink.repository;

import com.auralink.entity.PaintingCritique;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaintingCritiqueRepository extends JpaRepository<PaintingCritique, Long> {
   @EntityGraph(attributePaths = {"ownerUser", "painting", "imageAsset"})
   Optional<PaintingCritique> findByPublicIdAndOwnerUserId(String publicId, Long ownerUserId);

   @EntityGraph(attributePaths = {"ownerUser", "painting", "imageAsset"})
   Optional<PaintingCritique> findFirstByOwnerUserIdAndPaintingIdAndInputFingerprintAndStatusInOrderByCreatedAtDesc(
      Long ownerUserId, Long paintingId, String inputFingerprint, Collection<String> statuses
   );

   @EntityGraph(attributePaths = {"ownerUser", "painting", "imageAsset"})
   Optional<PaintingCritique> findFirstByOwnerUserIdAndPaintingIdOrderByCreatedAtDesc(Long ownerUserId, Long paintingId);

   @EntityGraph(attributePaths = {"ownerUser", "painting", "imageAsset"})
   Optional<PaintingCritique> findByPublicId(String publicId);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE painting_critiques SET status='RUNNING', started_at=COALESCE(started_at,:now), lease_expires_at=:leaseExpiresAt, updated_at=:now WHERE public_id=:publicId AND status='QUEUED'",
      nativeQuery = true
   )
   int claimQueued(@Param("publicId") String publicId, @Param("now") LocalDateTime now, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "UPDATE painting_critiques SET status='FAILED', error_code='INTERRUPTED', error_message='智能评析任务在服务中断后未完成，请手动重新发起', finished_at=:now, updated_at=:now WHERE status='RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at <= :now",
      nativeQuery = true
   )
   int failExpiredRunning(@Param("now") LocalDateTime now);
}

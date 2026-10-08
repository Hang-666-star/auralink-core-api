package com.auralink.critic.service;

import com.auralink.entity.MediaAsset;
import com.auralink.entity.Painting;
import com.auralink.entity.PaintingCritique;
import com.auralink.entity.User;
import com.auralink.repository.PaintingCritiqueRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaintingCritiqueTaskStore {
   private final PaintingCritiqueRepository repository;

   public PaintingCritiqueTaskStore(PaintingCritiqueRepository repository) {
      this.repository = repository;
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public PaintingCritiqueTaskStore.Admission admit(
      User owner,
      Painting painting,
      MediaAsset image,
      String titleSnapshot,
      String profile,
      String fingerprint,
      String evaluatorVersion,
      String modelIdentity,
      String promptSchemaVersion
   ) {
      Optional<PaintingCritique> reusable = this.findReuse(owner.getId(), painting.getId(), fingerprint);
      if (reusable.isPresent()) {
         return new PaintingCritiqueTaskStore.Admission(reusable.orElseThrow(), false, true);
      }

      PaintingCritique task = PaintingCritique.builder()
         .ownerUser(owner)
         .painting(painting)
         .imageAsset(image)
         .sourceImageSha256(image.getSha256())
         .titleSnapshot(titleSnapshot)
         .profile(profile)
         .inputFingerprint(fingerprint)
         .evaluatorVersion(evaluatorVersion)
         .modelIdentity(modelIdentity)
         .promptSchemaVersion(promptSchemaVersion)
         .status("QUEUED")
         .build();
      return new PaintingCritiqueTaskStore.Admission((PaintingCritique)this.repository.saveAndFlush(task), true, false);
   }

   @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<PaintingCritique> current(Long ownerId, Long paintingId) {
      return this.repository.findFirstByOwnerUserIdAndPaintingIdOrderByCreatedAtDesc(ownerId, paintingId);
   }

   @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<PaintingCritique> owned(String publicId, Long ownerId) {
      return this.repository.findByPublicIdAndOwnerUserId(publicId, ownerId);
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public Optional<PaintingCritique> claim(String publicId, Duration deadline) {
      LocalDateTime now = LocalDateTime.now();
      return this.repository.claimQueued(publicId, now, now.plus(deadline)) != 1 ? Optional.empty() : this.repository.findByPublicId(publicId);
   }

   @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<PaintingCritique> existingEquivalent(Long ownerId, Long paintingId, String fingerprint) {
      return this.findReuse(ownerId, paintingId, fingerprint);
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public int terminalizeExpiredRunning() {
      return this.repository.failExpiredRunning(LocalDateTime.now());
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public void succeed(String publicId, String canonicalJson) {
      this.repository.findByPublicId(publicId).filter(task -> "RUNNING".equals(task.getStatus())).ifPresent(task -> {
         task.setResultJson(canonicalJson);
         task.setStatus("SUCCEEDED");
         task.setErrorCode(null);
         task.setErrorMessage(null);
         task.setLeaseExpiresAt(null);
         task.setFinishedAt(LocalDateTime.now());
         this.repository.save(task);
      });
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public void fail(String publicId, String code, String message) {
      this.repository.findByPublicId(publicId).filter(task -> "RUNNING".equals(task.getStatus())).ifPresent(task -> {
         task.setStatus("FAILED");
         task.setErrorCode(code);
         task.setErrorMessage(message);
         task.setLeaseExpiresAt(null);
         task.setFinishedAt(LocalDateTime.now());
         this.repository.save(task);
      });
   }

   private Optional<PaintingCritique> findReuse(Long ownerId, Long paintingId, String fingerprint) {
      return this.repository
         .findFirstByOwnerUserIdAndPaintingIdAndInputFingerprintAndStatusInOrderByCreatedAtDesc(
            ownerId, paintingId, fingerprint, List.of("SUCCEEDED", "QUEUED", "RUNNING")
         );
   }

   public record Admission(PaintingCritique task, boolean dispatchRequired, boolean reusedSuccess) {
   }
}

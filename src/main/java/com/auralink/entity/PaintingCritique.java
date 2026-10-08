package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "painting_critiques",
   uniqueConstraints = @UniqueConstraint(name = "uq_painting_critiques_public_id", columnNames = "public_id"),
   indexes = {
         @Index(name = "idx_painting_critiques_owner_painting", columnList = "owner_user_id,painting_id,created_at"),
         @Index(name = "idx_painting_critiques_status_created", columnList = "status,created_at")
   }
)
public class PaintingCritique extends BasePublicIdEntity {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "owner_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_painting_critiques_owner"))
   private User ownerUser;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "painting_id", nullable = false, foreignKey = @ForeignKey(name = "fk_painting_critiques_painting"))
   private Painting painting;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "image_asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_painting_critiques_image"))
   private MediaAsset imageAsset;
   @Column(name = "source_image_sha256", nullable = false, length = 64)
   private String sourceImageSha256;
   @Column(name = "title_snapshot", columnDefinition = "TEXT")
   private String titleSnapshot;
   @Column(nullable = false, length = 64)
   private String profile;
   @Column(name = "input_fingerprint", nullable = false, length = 64)
   private String inputFingerprint;
   @Column(name = "evaluator_version", nullable = false, length = 64)
   private String evaluatorVersion;
   @Column(name = "model_identity", nullable = false, length = 255)
   private String modelIdentity;
   @Column(name = "prompt_schema_version", nullable = false, length = 64)
   private String promptSchemaVersion;
   @Column(nullable = false, length = 32)
   private String status;
   @Column(name = "result_json", columnDefinition = "TEXT")
   private String resultJson;
   @Column(name = "error_code", length = 128)
   private String errorCode;
   @Column(name = "error_message", columnDefinition = "TEXT")
   private String errorMessage;
   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;
   @Column(name = "started_at")
   private LocalDateTime startedAt;
   @Column(name = "lease_expires_at")
   private LocalDateTime leaseExpiresAt;
   @Column(name = "finished_at")
   private LocalDateTime finishedAt;
   @Column(name = "updated_at", nullable = false)
   private LocalDateTime updatedAt;

   @PrePersist
   protected void createTimestamps() {
      LocalDateTime now = LocalDateTime.now();
      this.createdAt = this.createdAt == null ? now : this.createdAt;
      this.updatedAt = this.updatedAt == null ? now : this.updatedAt;
   }

   @PreUpdate
   protected void updateTimestamp() {
      this.updatedAt = LocalDateTime.now();
   }

   protected PaintingCritique(final PaintingCritique.PaintingCritiqueBuilder<?, ?> b) {
      super(b);
      this.ownerUser = b.ownerUser;
      this.painting = b.painting;
      this.imageAsset = b.imageAsset;
      this.sourceImageSha256 = b.sourceImageSha256;
      this.titleSnapshot = b.titleSnapshot;
      this.profile = b.profile;
      this.inputFingerprint = b.inputFingerprint;
      this.evaluatorVersion = b.evaluatorVersion;
      this.modelIdentity = b.modelIdentity;
      this.promptSchemaVersion = b.promptSchemaVersion;
      this.status = b.status;
      this.resultJson = b.resultJson;
      this.errorCode = b.errorCode;
      this.errorMessage = b.errorMessage;
      this.createdAt = b.createdAt;
      this.startedAt = b.startedAt;
      this.leaseExpiresAt = b.leaseExpiresAt;
      this.finishedAt = b.finishedAt;
      this.updatedAt = b.updatedAt;
   }

   public static PaintingCritique.PaintingCritiqueBuilder<?, ?> builder() {
      return new PaintingCritique.PaintingCritiqueBuilderImpl();
   }

   public User getOwnerUser() {
      return this.ownerUser;
   }

   public Painting getPainting() {
      return this.painting;
   }

   public MediaAsset getImageAsset() {
      return this.imageAsset;
   }

   public String getSourceImageSha256() {
      return this.sourceImageSha256;
   }

   public String getTitleSnapshot() {
      return this.titleSnapshot;
   }

   public String getProfile() {
      return this.profile;
   }

   public String getInputFingerprint() {
      return this.inputFingerprint;
   }

   public String getEvaluatorVersion() {
      return this.evaluatorVersion;
   }

   public String getModelIdentity() {
      return this.modelIdentity;
   }

   public String getPromptSchemaVersion() {
      return this.promptSchemaVersion;
   }

   public String getStatus() {
      return this.status;
   }

   public String getResultJson() {
      return this.resultJson;
   }

   public String getErrorCode() {
      return this.errorCode;
   }

   public String getErrorMessage() {
      return this.errorMessage;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public LocalDateTime getStartedAt() {
      return this.startedAt;
   }

   public LocalDateTime getLeaseExpiresAt() {
      return this.leaseExpiresAt;
   }

   public LocalDateTime getFinishedAt() {
      return this.finishedAt;
   }

   public LocalDateTime getUpdatedAt() {
      return this.updatedAt;
   }

   public void setOwnerUser(final User ownerUser) {
      this.ownerUser = ownerUser;
   }

   public void setPainting(final Painting painting) {
      this.painting = painting;
   }

   public void setImageAsset(final MediaAsset imageAsset) {
      this.imageAsset = imageAsset;
   }

   public void setSourceImageSha256(final String sourceImageSha256) {
      this.sourceImageSha256 = sourceImageSha256;
   }

   public void setTitleSnapshot(final String titleSnapshot) {
      this.titleSnapshot = titleSnapshot;
   }

   public void setProfile(final String profile) {
      this.profile = profile;
   }

   public void setInputFingerprint(final String inputFingerprint) {
      this.inputFingerprint = inputFingerprint;
   }

   public void setEvaluatorVersion(final String evaluatorVersion) {
      this.evaluatorVersion = evaluatorVersion;
   }

   public void setModelIdentity(final String modelIdentity) {
      this.modelIdentity = modelIdentity;
   }

   public void setPromptSchemaVersion(final String promptSchemaVersion) {
      this.promptSchemaVersion = promptSchemaVersion;
   }

   public void setStatus(final String status) {
      this.status = status;
   }

   public void setResultJson(final String resultJson) {
      this.resultJson = resultJson;
   }

   public void setErrorCode(final String errorCode) {
      this.errorCode = errorCode;
   }

   public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public void setStartedAt(final LocalDateTime startedAt) {
      this.startedAt = startedAt;
   }

   public void setLeaseExpiresAt(final LocalDateTime leaseExpiresAt) {
      this.leaseExpiresAt = leaseExpiresAt;
   }

   public void setFinishedAt(final LocalDateTime finishedAt) {
      this.finishedAt = finishedAt;
   }

   public void setUpdatedAt(final LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
   }

   public PaintingCritique() {
   }

   public PaintingCritique(
      final User ownerUser,
      final Painting painting,
      final MediaAsset imageAsset,
      final String sourceImageSha256,
      final String titleSnapshot,
      final String profile,
      final String inputFingerprint,
      final String evaluatorVersion,
      final String modelIdentity,
      final String promptSchemaVersion,
      final String status,
      final String resultJson,
      final String errorCode,
      final String errorMessage,
      final LocalDateTime createdAt,
      final LocalDateTime startedAt,
      final LocalDateTime leaseExpiresAt,
      final LocalDateTime finishedAt,
      final LocalDateTime updatedAt
   ) {
      this.ownerUser = ownerUser;
      this.painting = painting;
      this.imageAsset = imageAsset;
      this.sourceImageSha256 = sourceImageSha256;
      this.titleSnapshot = titleSnapshot;
      this.profile = profile;
      this.inputFingerprint = inputFingerprint;
      this.evaluatorVersion = evaluatorVersion;
      this.modelIdentity = modelIdentity;
      this.promptSchemaVersion = promptSchemaVersion;
      this.status = status;
      this.resultJson = resultJson;
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
      this.createdAt = createdAt;
      this.startedAt = startedAt;
      this.leaseExpiresAt = leaseExpiresAt;
      this.finishedAt = finishedAt;
      this.updatedAt = updatedAt;
   }

   public abstract static class PaintingCritiqueBuilder<C extends PaintingCritique, B extends PaintingCritique.PaintingCritiqueBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private User ownerUser;
      private Painting painting;
      private MediaAsset imageAsset;
      private String sourceImageSha256;
      private String titleSnapshot;
      private String profile;
      private String inputFingerprint;
      private String evaluatorVersion;
      private String modelIdentity;
      private String promptSchemaVersion;
      private String status;
      private String resultJson;
      private String errorCode;
      private String errorMessage;
      private LocalDateTime createdAt;
      private LocalDateTime startedAt;
      private LocalDateTime leaseExpiresAt;
      private LocalDateTime finishedAt;
      private LocalDateTime updatedAt;

      public B ownerUser(final User ownerUser) {
         this.ownerUser = ownerUser;
         return this.self();
      }

      public B painting(final Painting painting) {
         this.painting = painting;
         return this.self();
      }

      public B imageAsset(final MediaAsset imageAsset) {
         this.imageAsset = imageAsset;
         return this.self();
      }

      public B sourceImageSha256(final String sourceImageSha256) {
         this.sourceImageSha256 = sourceImageSha256;
         return this.self();
      }

      public B titleSnapshot(final String titleSnapshot) {
         this.titleSnapshot = titleSnapshot;
         return this.self();
      }

      public B profile(final String profile) {
         this.profile = profile;
         return this.self();
      }

      public B inputFingerprint(final String inputFingerprint) {
         this.inputFingerprint = inputFingerprint;
         return this.self();
      }

      public B evaluatorVersion(final String evaluatorVersion) {
         this.evaluatorVersion = evaluatorVersion;
         return this.self();
      }

      public B modelIdentity(final String modelIdentity) {
         this.modelIdentity = modelIdentity;
         return this.self();
      }

      public B promptSchemaVersion(final String promptSchemaVersion) {
         this.promptSchemaVersion = promptSchemaVersion;
         return this.self();
      }

      public B status(final String status) {
         this.status = status;
         return this.self();
      }

      public B resultJson(final String resultJson) {
         this.resultJson = resultJson;
         return this.self();
      }

      public B errorCode(final String errorCode) {
         this.errorCode = errorCode;
         return this.self();
      }

      public B errorMessage(final String errorMessage) {
         this.errorMessage = errorMessage;
         return this.self();
      }

      public B createdAt(final LocalDateTime createdAt) {
         this.createdAt = createdAt;
         return this.self();
      }

      public B startedAt(final LocalDateTime startedAt) {
         this.startedAt = startedAt;
         return this.self();
      }

      public B leaseExpiresAt(final LocalDateTime leaseExpiresAt) {
         this.leaseExpiresAt = leaseExpiresAt;
         return this.self();
      }

      public B finishedAt(final LocalDateTime finishedAt) {
         this.finishedAt = finishedAt;
         return this.self();
      }

      public B updatedAt(final LocalDateTime updatedAt) {
         this.updatedAt = updatedAt;
         return this.self();
      }

      protected abstract B self();

      public abstract C build();

      @Override
      public String toString() {
         return "PaintingCritique.PaintingCritiqueBuilder(super="
            + super.toString()
            + ", ownerUser="
            + this.ownerUser
            + ", painting="
            + this.painting
            + ", imageAsset="
            + this.imageAsset
            + ", sourceImageSha256="
            + this.sourceImageSha256
            + ", titleSnapshot="
            + this.titleSnapshot
            + ", profile="
            + this.profile
            + ", inputFingerprint="
            + this.inputFingerprint
            + ", evaluatorVersion="
            + this.evaluatorVersion
            + ", modelIdentity="
            + this.modelIdentity
            + ", promptSchemaVersion="
            + this.promptSchemaVersion
            + ", status="
            + this.status
            + ", resultJson="
            + this.resultJson
            + ", errorCode="
            + this.errorCode
            + ", errorMessage="
            + this.errorMessage
            + ", createdAt="
            + this.createdAt
            + ", startedAt="
            + this.startedAt
            + ", leaseExpiresAt="
            + this.leaseExpiresAt
            + ", finishedAt="
            + this.finishedAt
            + ", updatedAt="
            + this.updatedAt
            + ")";
      }
   }

   private static final class PaintingCritiqueBuilderImpl
      extends PaintingCritique.PaintingCritiqueBuilder<PaintingCritique, PaintingCritique.PaintingCritiqueBuilderImpl> {
      protected PaintingCritique.PaintingCritiqueBuilderImpl self() {
         return this;
      }

      @Override
      public PaintingCritique build() {
         return new PaintingCritique(this);
      }
   }
}

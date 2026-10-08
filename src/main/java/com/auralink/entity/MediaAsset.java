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
   name = "media_assets",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_media_assets_public_id", columnNames = "public_id"),
         @UniqueConstraint(name = "uq_media_assets_storage_key", columnNames = "storage_key")
   },
   indexes = {
         @Index(name = "idx_media_assets_owner_created", columnList = "owner_user_id, created_at"),
         @Index(name = "idx_media_assets_type_status", columnList = "asset_type, semantic_type, status"),
         @Index(name = "idx_media_assets_sha256", columnList = "sha256")
   }
)
public class MediaAsset extends BasePublicIdEntity {
   @ManyToOne(fetch = FetchType.LAZY, optional = true)
   @JoinColumn(name = "owner_user_id", foreignKey = @ForeignKey(name = "fk_media_assets_owner_user"))
   private User ownerUser;
   @Column(name = "storage_key", nullable = false, length = 1024)
   private String storageKey;
   @Column(name = "original_filename", nullable = false, length = 512)
   private String originalFilename;
   @Column(name = "mime_type", length = 255)
   private String mimeType;
   @Column(name = "file_size")
   private Long fileSize;
   @Column(length = 64)
   private String sha256;
   private Integer width;
   private Integer height;
   @Column(name = "duration_seconds")
   private Double durationSeconds;
   @Column(name = "asset_type", nullable = false, length = 64)
   private String assetType;
   @Column(name = "semantic_type", nullable = false, length = 64)
   private String semanticType;
   @Column(name = "source_type", nullable = false, length = 64)
   private String sourceType;
   @Column(nullable = false, length = 64)
   private String visibility;
   @Column(nullable = false, length = 64)
   private String status;
   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;
   @Column(name = "updated_at", nullable = false)
   private LocalDateTime updatedAt;

   @PrePersist
   protected void initializeTimestamps() {
      LocalDateTime now = LocalDateTime.now();
      if (this.createdAt == null) {
         this.createdAt = now;
      }

      if (this.updatedAt == null) {
         this.updatedAt = now;
      }
   }

   @PreUpdate
   protected void updateTimestamp() {
      this.updatedAt = LocalDateTime.now();
   }

   protected MediaAsset(final MediaAsset.MediaAssetBuilder<?, ?> b) {
      super(b);
      this.ownerUser = b.ownerUser;
      this.storageKey = b.storageKey;
      this.originalFilename = b.originalFilename;
      this.mimeType = b.mimeType;
      this.fileSize = b.fileSize;
      this.sha256 = b.sha256;
      this.width = b.width;
      this.height = b.height;
      this.durationSeconds = b.durationSeconds;
      this.assetType = b.assetType;
      this.semanticType = b.semanticType;
      this.sourceType = b.sourceType;
      this.visibility = b.visibility;
      this.status = b.status;
      this.createdAt = b.createdAt;
      this.updatedAt = b.updatedAt;
   }

   public static MediaAsset.MediaAssetBuilder<?, ?> builder() {
      return new MediaAsset.MediaAssetBuilderImpl();
   }

   public User getOwnerUser() {
      return this.ownerUser;
   }

   public String getStorageKey() {
      return this.storageKey;
   }

   public String getOriginalFilename() {
      return this.originalFilename;
   }

   public String getMimeType() {
      return this.mimeType;
   }

   public Long getFileSize() {
      return this.fileSize;
   }

   public String getSha256() {
      return this.sha256;
   }

   public Integer getWidth() {
      return this.width;
   }

   public Integer getHeight() {
      return this.height;
   }

   public Double getDurationSeconds() {
      return this.durationSeconds;
   }

   public String getAssetType() {
      return this.assetType;
   }

   public String getSemanticType() {
      return this.semanticType;
   }

   public String getSourceType() {
      return this.sourceType;
   }

   public String getVisibility() {
      return this.visibility;
   }

   public String getStatus() {
      return this.status;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public LocalDateTime getUpdatedAt() {
      return this.updatedAt;
   }

   public void setOwnerUser(final User ownerUser) {
      this.ownerUser = ownerUser;
   }

   public void setStorageKey(final String storageKey) {
      this.storageKey = storageKey;
   }

   public void setOriginalFilename(final String originalFilename) {
      this.originalFilename = originalFilename;
   }

   public void setMimeType(final String mimeType) {
      this.mimeType = mimeType;
   }

   public void setFileSize(final Long fileSize) {
      this.fileSize = fileSize;
   }

   public void setSha256(final String sha256) {
      this.sha256 = sha256;
   }

   public void setWidth(final Integer width) {
      this.width = width;
   }

   public void setHeight(final Integer height) {
      this.height = height;
   }

   public void setDurationSeconds(final Double durationSeconds) {
      this.durationSeconds = durationSeconds;
   }

   public void setAssetType(final String assetType) {
      this.assetType = assetType;
   }

   public void setSemanticType(final String semanticType) {
      this.semanticType = semanticType;
   }

   public void setSourceType(final String sourceType) {
      this.sourceType = sourceType;
   }

   public void setVisibility(final String visibility) {
      this.visibility = visibility;
   }

   public void setStatus(final String status) {
      this.status = status;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public void setUpdatedAt(final LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
   }

   public MediaAsset() {
   }

   public MediaAsset(
      final User ownerUser,
      final String storageKey,
      final String originalFilename,
      final String mimeType,
      final Long fileSize,
      final String sha256,
      final Integer width,
      final Integer height,
      final Double durationSeconds,
      final String assetType,
      final String semanticType,
      final String sourceType,
      final String visibility,
      final String status,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt
   ) {
      this.ownerUser = ownerUser;
      this.storageKey = storageKey;
      this.originalFilename = originalFilename;
      this.mimeType = mimeType;
      this.fileSize = fileSize;
      this.sha256 = sha256;
      this.width = width;
      this.height = height;
      this.durationSeconds = durationSeconds;
      this.assetType = assetType;
      this.semanticType = semanticType;
      this.sourceType = sourceType;
      this.visibility = visibility;
      this.status = status;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
   }

   public abstract static class MediaAssetBuilder<C extends MediaAsset, B extends MediaAsset.MediaAssetBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private User ownerUser;
      private String storageKey;
      private String originalFilename;
      private String mimeType;
      private Long fileSize;
      private String sha256;
      private Integer width;
      private Integer height;
      private Double durationSeconds;
      private String assetType;
      private String semanticType;
      private String sourceType;
      private String visibility;
      private String status;
      private LocalDateTime createdAt;
      private LocalDateTime updatedAt;

      public B ownerUser(final User ownerUser) {
         this.ownerUser = ownerUser;
         return this.self();
      }

      public B storageKey(final String storageKey) {
         this.storageKey = storageKey;
         return this.self();
      }

      public B originalFilename(final String originalFilename) {
         this.originalFilename = originalFilename;
         return this.self();
      }

      public B mimeType(final String mimeType) {
         this.mimeType = mimeType;
         return this.self();
      }

      public B fileSize(final Long fileSize) {
         this.fileSize = fileSize;
         return this.self();
      }

      public B sha256(final String sha256) {
         this.sha256 = sha256;
         return this.self();
      }

      public B width(final Integer width) {
         this.width = width;
         return this.self();
      }

      public B height(final Integer height) {
         this.height = height;
         return this.self();
      }

      public B durationSeconds(final Double durationSeconds) {
         this.durationSeconds = durationSeconds;
         return this.self();
      }

      public B assetType(final String assetType) {
         this.assetType = assetType;
         return this.self();
      }

      public B semanticType(final String semanticType) {
         this.semanticType = semanticType;
         return this.self();
      }

      public B sourceType(final String sourceType) {
         this.sourceType = sourceType;
         return this.self();
      }

      public B visibility(final String visibility) {
         this.visibility = visibility;
         return this.self();
      }

      public B status(final String status) {
         this.status = status;
         return this.self();
      }

      public B createdAt(final LocalDateTime createdAt) {
         this.createdAt = createdAt;
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
         return "MediaAsset.MediaAssetBuilder(super="
            + super.toString()
            + ", ownerUser="
            + this.ownerUser
            + ", storageKey="
            + this.storageKey
            + ", originalFilename="
            + this.originalFilename
            + ", mimeType="
            + this.mimeType
            + ", fileSize="
            + this.fileSize
            + ", sha256="
            + this.sha256
            + ", width="
            + this.width
            + ", height="
            + this.height
            + ", durationSeconds="
            + this.durationSeconds
            + ", assetType="
            + this.assetType
            + ", semanticType="
            + this.semanticType
            + ", sourceType="
            + this.sourceType
            + ", visibility="
            + this.visibility
            + ", status="
            + this.status
            + ", createdAt="
            + this.createdAt
            + ", updatedAt="
            + this.updatedAt
            + ")";
      }
   }

   private static final class MediaAssetBuilderImpl extends MediaAsset.MediaAssetBuilder<MediaAsset, MediaAsset.MediaAssetBuilderImpl> {
      protected MediaAsset.MediaAssetBuilderImpl self() {
         return this;
      }

      @Override
      public MediaAsset build() {
         return new MediaAsset(this);
      }
   }
}

package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "painting_guides",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_painting_guides_public_id", columnNames = "public_id"),
         @UniqueConstraint(name = "uq_painting_guides_painting", columnNames = "painting_id")
   },
   indexes = {@Index(name = "idx_painting_guides_status", columnList = "status"), @Index(name = "idx_painting_guides_source_hash", columnList = "source_hash")}
)
public class PaintingGuide extends BasePublicIdEntity {
   @OneToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "painting_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_painting_guides_painting"))
   private Painting painting;
   @Column(name = "result_json", columnDefinition = "TEXT")
   private String resultJson;
   @Column(name = "source_hash", length = 64)
   private String sourceHash;
   @Column(nullable = false, length = 64)
   private String status;
   @Column(name = "generated_at")
   private LocalDateTime generatedAt;
   @Column(name = "updated_at", nullable = false)
   private LocalDateTime updatedAt;

   @PrePersist
   protected void initializeUpdatedAt() {
      if (this.updatedAt == null) {
         this.updatedAt = LocalDateTime.now();
      }
   }

   @PreUpdate
   protected void updateTimestamp() {
      this.updatedAt = LocalDateTime.now();
   }

   protected PaintingGuide(final PaintingGuide.PaintingGuideBuilder<?, ?> b) {
      super(b);
      this.painting = b.painting;
      this.resultJson = b.resultJson;
      this.sourceHash = b.sourceHash;
      this.status = b.status;
      this.generatedAt = b.generatedAt;
      this.updatedAt = b.updatedAt;
   }

   public static PaintingGuide.PaintingGuideBuilder<?, ?> builder() {
      return new PaintingGuide.PaintingGuideBuilderImpl();
   }

   public Painting getPainting() {
      return this.painting;
   }

   public String getResultJson() {
      return this.resultJson;
   }

   public String getSourceHash() {
      return this.sourceHash;
   }

   public String getStatus() {
      return this.status;
   }

   public LocalDateTime getGeneratedAt() {
      return this.generatedAt;
   }

   public LocalDateTime getUpdatedAt() {
      return this.updatedAt;
   }

   public void setPainting(final Painting painting) {
      this.painting = painting;
   }

   public void setResultJson(final String resultJson) {
      this.resultJson = resultJson;
   }

   public void setSourceHash(final String sourceHash) {
      this.sourceHash = sourceHash;
   }

   public void setStatus(final String status) {
      this.status = status;
   }

   public void setGeneratedAt(final LocalDateTime generatedAt) {
      this.generatedAt = generatedAt;
   }

   public void setUpdatedAt(final LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
   }

   public PaintingGuide() {
   }

   public PaintingGuide(
      final Painting painting,
      final String resultJson,
      final String sourceHash,
      final String status,
      final LocalDateTime generatedAt,
      final LocalDateTime updatedAt
   ) {
      this.painting = painting;
      this.resultJson = resultJson;
      this.sourceHash = sourceHash;
      this.status = status;
      this.generatedAt = generatedAt;
      this.updatedAt = updatedAt;
   }

   public abstract static class PaintingGuideBuilder<C extends PaintingGuide, B extends PaintingGuide.PaintingGuideBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private Painting painting;
      private String resultJson;
      private String sourceHash;
      private String status;
      private LocalDateTime generatedAt;
      private LocalDateTime updatedAt;

      public B painting(final Painting painting) {
         this.painting = painting;
         return this.self();
      }

      public B resultJson(final String resultJson) {
         this.resultJson = resultJson;
         return this.self();
      }

      public B sourceHash(final String sourceHash) {
         this.sourceHash = sourceHash;
         return this.self();
      }

      public B status(final String status) {
         this.status = status;
         return this.self();
      }

      public B generatedAt(final LocalDateTime generatedAt) {
         this.generatedAt = generatedAt;
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
         return "PaintingGuide.PaintingGuideBuilder(super="
            + super.toString()
            + ", painting="
            + this.painting
            + ", resultJson="
            + this.resultJson
            + ", sourceHash="
            + this.sourceHash
            + ", status="
            + this.status
            + ", generatedAt="
            + this.generatedAt
            + ", updatedAt="
            + this.updatedAt
            + ")";
      }
   }

   private static final class PaintingGuideBuilderImpl extends PaintingGuide.PaintingGuideBuilder<PaintingGuide, PaintingGuide.PaintingGuideBuilderImpl> {
      protected PaintingGuide.PaintingGuideBuilderImpl self() {
         return this;
      }

      @Override
      public PaintingGuide build() {
         return new PaintingGuide(this);
      }
   }
}

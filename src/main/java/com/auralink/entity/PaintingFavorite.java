package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "painting_favorites",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_painting_favorites_public_id", columnNames = "public_id"),
         @UniqueConstraint(name = "uq_painting_favorites_user_painting", columnNames = {"user_id", "painting_id"})
   },
   indexes = {
         @Index(name = "idx_painting_favorites_user_created", columnList = "user_id, created_at"),
         @Index(name = "idx_painting_favorites_painting_user", columnList = "painting_id, user_id")
   }
)
public class PaintingFavorite extends BasePublicIdEntity {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_painting_favorites_user"))
   private User user;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "painting_id", nullable = false, foreignKey = @ForeignKey(name = "fk_painting_favorites_painting"))
   private Painting painting;
   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;

   @PrePersist
   protected void initializeCreatedAt() {
      if (this.createdAt == null) {
         this.createdAt = LocalDateTime.now();
      }
   }

   protected PaintingFavorite(final PaintingFavorite.PaintingFavoriteBuilder<?, ?> b) {
      super(b);
      this.user = b.user;
      this.painting = b.painting;
      this.createdAt = b.createdAt;
   }

   public static PaintingFavorite.PaintingFavoriteBuilder<?, ?> builder() {
      return new PaintingFavorite.PaintingFavoriteBuilderImpl();
   }

   public User getUser() {
      return this.user;
   }

   public Painting getPainting() {
      return this.painting;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public void setUser(final User user) {
      this.user = user;
   }

   public void setPainting(final Painting painting) {
      this.painting = painting;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public PaintingFavorite() {
   }

   public PaintingFavorite(final User user, final Painting painting, final LocalDateTime createdAt) {
      this.user = user;
      this.painting = painting;
      this.createdAt = createdAt;
   }

   public abstract static class PaintingFavoriteBuilder<C extends PaintingFavorite, B extends PaintingFavorite.PaintingFavoriteBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private User user;
      private Painting painting;
      private LocalDateTime createdAt;

      public B user(final User user) {
         this.user = user;
         return this.self();
      }

      public B painting(final Painting painting) {
         this.painting = painting;
         return this.self();
      }

      public B createdAt(final LocalDateTime createdAt) {
         this.createdAt = createdAt;
         return this.self();
      }

      protected abstract B self();

      public abstract C build();

      @Override
      public String toString() {
         return "PaintingFavorite.PaintingFavoriteBuilder(super="
            + super.toString()
            + ", user="
            + this.user
            + ", painting="
            + this.painting
            + ", createdAt="
            + this.createdAt
            + ")";
      }
   }

   private static final class PaintingFavoriteBuilderImpl
      extends PaintingFavorite.PaintingFavoriteBuilder<PaintingFavorite, PaintingFavorite.PaintingFavoriteBuilderImpl> {
      protected PaintingFavorite.PaintingFavoriteBuilderImpl self() {
         return this;
      }

      @Override
      public PaintingFavorite build() {
         return new PaintingFavorite(this);
      }
   }
}

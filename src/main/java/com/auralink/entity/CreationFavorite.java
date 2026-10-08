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
   name = "creation_favorites",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_creation_favorites_public_id", columnNames = "public_id"),
         @UniqueConstraint(name = "uq_creation_favorites_user_creation", columnNames = {"user_id", "creation_id"})
   },
   indexes = {
         @Index(name = "idx_creation_favorites_user_created", columnList = "user_id, created_at"),
         @Index(name = "idx_creation_favorites_user_created_public", columnList = "user_id, created_at, public_id"),
         @Index(name = "idx_creation_favorites_creation_user", columnList = "creation_id, user_id")
   }
)
public class CreationFavorite extends BasePublicIdEntity {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_creation_favorites_user"))
   private User user;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "creation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_creation_favorites_creation"))
   private Creation creation;
   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;

   @PrePersist
   protected void initializeCreatedAt() {
      if (this.createdAt == null) {
         this.createdAt = LocalDateTime.now();
      }
   }

   protected CreationFavorite(final CreationFavorite.CreationFavoriteBuilder<?, ?> b) {
      super(b);
      this.user = b.user;
      this.creation = b.creation;
      this.createdAt = b.createdAt;
   }

   public static CreationFavorite.CreationFavoriteBuilder<?, ?> builder() {
      return new CreationFavorite.CreationFavoriteBuilderImpl();
   }

   public User getUser() {
      return this.user;
   }

   public Creation getCreation() {
      return this.creation;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public void setUser(final User user) {
      this.user = user;
   }

   public void setCreation(final Creation creation) {
      this.creation = creation;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public CreationFavorite() {
   }

   public CreationFavorite(final User user, final Creation creation, final LocalDateTime createdAt) {
      this.user = user;
      this.creation = creation;
      this.createdAt = createdAt;
   }

   public abstract static class CreationFavoriteBuilder<C extends CreationFavorite, B extends CreationFavorite.CreationFavoriteBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private User user;
      private Creation creation;
      private LocalDateTime createdAt;

      public B user(final User user) {
         this.user = user;
         return this.self();
      }

      public B creation(final Creation creation) {
         this.creation = creation;
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
         return "CreationFavorite.CreationFavoriteBuilder(super="
            + super.toString()
            + ", user="
            + this.user
            + ", creation="
            + this.creation
            + ", createdAt="
            + this.createdAt
            + ")";
      }
   }

   private static final class CreationFavoriteBuilderImpl
      extends CreationFavorite.CreationFavoriteBuilder<CreationFavorite, CreationFavorite.CreationFavoriteBuilderImpl> {
      protected CreationFavorite.CreationFavoriteBuilderImpl self() {
         return this;
      }

      @Override
      public CreationFavorite build() {
         return new CreationFavorite(this);
      }
   }
}

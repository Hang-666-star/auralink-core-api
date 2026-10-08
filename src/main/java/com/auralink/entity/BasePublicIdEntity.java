package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class BasePublicIdEntity {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;
   @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
   private String publicId;

   @PrePersist
   protected void ensurePublicId() {
      if (this.publicId != null && !this.publicId.isBlank()) {
         this.publicId = UUID.fromString(this.publicId).toString();
      } else {
         this.publicId = UUID.randomUUID().toString();
      }
   }

   protected BasePublicIdEntity(final BasePublicIdEntity.BasePublicIdEntityBuilder<?, ?> b) {
      this.id = b.id;
      this.publicId = b.publicId;
   }

   public Long getId() {
      return this.id;
   }

   public String getPublicId() {
      return this.publicId;
   }

   public void setId(final Long id) {
      this.id = id;
   }

   public void setPublicId(final String publicId) {
      this.publicId = publicId;
   }

   public BasePublicIdEntity() {
   }

   public BasePublicIdEntity(final Long id, final String publicId) {
      this.id = id;
      this.publicId = publicId;
   }

   public abstract static class BasePublicIdEntityBuilder<C extends BasePublicIdEntity, B extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B>> {
      private Long id;
      private String publicId;

      public B id(final Long id) {
         this.id = id;
         return this.self();
      }

      public B publicId(final String publicId) {
         this.publicId = publicId;
         return this.self();
      }

      protected abstract B self();

      public abstract C build();

      @Override
      public String toString() {
         return "BasePublicIdEntity.BasePublicIdEntityBuilder(id=" + this.id + ", publicId=" + this.publicId + ")";
      }
   }
}

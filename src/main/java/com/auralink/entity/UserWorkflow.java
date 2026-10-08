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
   name = "user_workflows",
   uniqueConstraints = @UniqueConstraint(name = "uq_user_workflows_public_id", columnNames = "public_id"),
   indexes = @Index(name = "idx_user_workflows_user_status_updated", columnList = "user_id, status, updated_at")
)
public class UserWorkflow extends BasePublicIdEntity {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_workflows_user"))
   private User user;
   @Column(nullable = false, length = 255)
   private String name;
   @Column(columnDefinition = "TEXT")
   private String description;
   @Column(name = "graph_json", nullable = false, columnDefinition = "TEXT")
   private String graphJson;
   @Column(name = "schema_version", nullable = false)
   private int schemaVersion;
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

   protected UserWorkflow(final UserWorkflow.UserWorkflowBuilder<?, ?> b) {
      super(b);
      this.user = b.user;
      this.name = b.name;
      this.description = b.description;
      this.graphJson = b.graphJson;
      this.schemaVersion = b.schemaVersion;
      this.status = b.status;
      this.createdAt = b.createdAt;
      this.updatedAt = b.updatedAt;
   }

   public static UserWorkflow.UserWorkflowBuilder<?, ?> builder() {
      return new UserWorkflow.UserWorkflowBuilderImpl();
   }

   public User getUser() {
      return this.user;
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public String getGraphJson() {
      return this.graphJson;
   }

   public int getSchemaVersion() {
      return this.schemaVersion;
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

   public void setUser(final User user) {
      this.user = user;
   }

   public void setName(final String name) {
      this.name = name;
   }

   public void setDescription(final String description) {
      this.description = description;
   }

   public void setGraphJson(final String graphJson) {
      this.graphJson = graphJson;
   }

   public void setSchemaVersion(final int schemaVersion) {
      this.schemaVersion = schemaVersion;
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

   public UserWorkflow() {
   }

   public UserWorkflow(
      final User user,
      final String name,
      final String description,
      final String graphJson,
      final int schemaVersion,
      final String status,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt
   ) {
      this.user = user;
      this.name = name;
      this.description = description;
      this.graphJson = graphJson;
      this.schemaVersion = schemaVersion;
      this.status = status;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
   }

   public abstract static class UserWorkflowBuilder<C extends UserWorkflow, B extends UserWorkflow.UserWorkflowBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private User user;
      private String name;
      private String description;
      private String graphJson;
      private int schemaVersion;
      private String status;
      private LocalDateTime createdAt;
      private LocalDateTime updatedAt;

      public B user(final User user) {
         this.user = user;
         return this.self();
      }

      public B name(final String name) {
         this.name = name;
         return this.self();
      }

      public B description(final String description) {
         this.description = description;
         return this.self();
      }

      public B graphJson(final String graphJson) {
         this.graphJson = graphJson;
         return this.self();
      }

      public B schemaVersion(final int schemaVersion) {
         this.schemaVersion = schemaVersion;
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
         return "UserWorkflow.UserWorkflowBuilder(super="
            + super.toString()
            + ", user="
            + this.user
            + ", name="
            + this.name
            + ", description="
            + this.description
            + ", graphJson="
            + this.graphJson
            + ", schemaVersion="
            + this.schemaVersion
            + ", status="
            + this.status
            + ", createdAt="
            + this.createdAt
            + ", updatedAt="
            + this.updatedAt
            + ")";
      }
   }

   private static final class UserWorkflowBuilderImpl extends UserWorkflow.UserWorkflowBuilder<UserWorkflow, UserWorkflow.UserWorkflowBuilderImpl> {
      protected UserWorkflow.UserWorkflowBuilderImpl self() {
         return this;
      }

      @Override
      public UserWorkflow build() {
         return new UserWorkflow(this);
      }
   }
}

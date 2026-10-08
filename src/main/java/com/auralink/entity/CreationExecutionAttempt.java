package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "creation_execution_attempts",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_creation_execution_attempts_creation_number", columnNames = {"creation_id", "attempt_number"}),
         @UniqueConstraint(name = "uq_creation_execution_attempts_creation_idempotency", columnNames = {"creation_id", "retry_idempotency_key_digest"})
   },
   indexes = @Index(name = "idx_creation_execution_attempts_creation_admitted", columnList = "creation_id, admitted_at, id")
)
public class CreationExecutionAttempt {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "creation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_creation_execution_attempts_creation"))
   private Creation creation;
   @Column(name = "attempt_number", nullable = false)
   private int attemptNumber;
   @Column(name = "retry_idempotency_key_digest", length = 64)
   private String retryIdempotencyKeyDigest;
   @Column(name = "admitted_at", nullable = false)
   private LocalDateTime admittedAt;
   @Column(name = "finished_at")
   private LocalDateTime finishedAt;
   @Column(name = "resolution_code", length = 128)
   private String resolutionCode;

   public static CreationExecutionAttempt.CreationExecutionAttemptBuilder builder() {
      return new CreationExecutionAttempt.CreationExecutionAttemptBuilder();
   }

   public Long getId() {
      return this.id;
   }

   public Creation getCreation() {
      return this.creation;
   }

   public int getAttemptNumber() {
      return this.attemptNumber;
   }

   public String getRetryIdempotencyKeyDigest() {
      return this.retryIdempotencyKeyDigest;
   }

   public LocalDateTime getAdmittedAt() {
      return this.admittedAt;
   }

   public LocalDateTime getFinishedAt() {
      return this.finishedAt;
   }

   public String getResolutionCode() {
      return this.resolutionCode;
   }

   public void setId(final Long id) {
      this.id = id;
   }

   public void setCreation(final Creation creation) {
      this.creation = creation;
   }

   public void setAttemptNumber(final int attemptNumber) {
      this.attemptNumber = attemptNumber;
   }

   public void setRetryIdempotencyKeyDigest(final String retryIdempotencyKeyDigest) {
      this.retryIdempotencyKeyDigest = retryIdempotencyKeyDigest;
   }

   public void setAdmittedAt(final LocalDateTime admittedAt) {
      this.admittedAt = admittedAt;
   }

   public void setFinishedAt(final LocalDateTime finishedAt) {
      this.finishedAt = finishedAt;
   }

   public void setResolutionCode(final String resolutionCode) {
      this.resolutionCode = resolutionCode;
   }

   public CreationExecutionAttempt() {
   }

   public CreationExecutionAttempt(
      final Long id,
      final Creation creation,
      final int attemptNumber,
      final String retryIdempotencyKeyDigest,
      final LocalDateTime admittedAt,
      final LocalDateTime finishedAt,
      final String resolutionCode
   ) {
      this.id = id;
      this.creation = creation;
      this.attemptNumber = attemptNumber;
      this.retryIdempotencyKeyDigest = retryIdempotencyKeyDigest;
      this.admittedAt = admittedAt;
      this.finishedAt = finishedAt;
      this.resolutionCode = resolutionCode;
   }

   public static class CreationExecutionAttemptBuilder {
      private Long id;
      private Creation creation;
      private int attemptNumber;
      private String retryIdempotencyKeyDigest;
      private LocalDateTime admittedAt;
      private LocalDateTime finishedAt;
      private String resolutionCode;

      CreationExecutionAttemptBuilder() {
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder id(final Long id) {
         this.id = id;
         return this;
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder creation(final Creation creation) {
         this.creation = creation;
         return this;
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder attemptNumber(final int attemptNumber) {
         this.attemptNumber = attemptNumber;
         return this;
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder retryIdempotencyKeyDigest(final String retryIdempotencyKeyDigest) {
         this.retryIdempotencyKeyDigest = retryIdempotencyKeyDigest;
         return this;
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder admittedAt(final LocalDateTime admittedAt) {
         this.admittedAt = admittedAt;
         return this;
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder finishedAt(final LocalDateTime finishedAt) {
         this.finishedAt = finishedAt;
         return this;
      }

      public CreationExecutionAttempt.CreationExecutionAttemptBuilder resolutionCode(final String resolutionCode) {
         this.resolutionCode = resolutionCode;
         return this;
      }

      public CreationExecutionAttempt build() {
         return new CreationExecutionAttempt(
            this.id, this.creation, this.attemptNumber, this.retryIdempotencyKeyDigest, this.admittedAt, this.finishedAt, this.resolutionCode
         );
      }

      @Override
      public String toString() {
         return "CreationExecutionAttempt.CreationExecutionAttemptBuilder(id="
            + this.id
            + ", creation="
            + this.creation
            + ", attemptNumber="
            + this.attemptNumber
            + ", retryIdempotencyKeyDigest="
            + this.retryIdempotencyKeyDigest
            + ", admittedAt="
            + this.admittedAt
            + ", finishedAt="
            + this.finishedAt
            + ", resolutionCode="
            + this.resolutionCode
            + ")";
      }
   }
}

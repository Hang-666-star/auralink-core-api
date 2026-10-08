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
   name = "creation_step_dispatch_attempts",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_creation_step_dispatch_attempts_step_execution", columnNames = {"creation_step_id", "creation_execution_attempt_id"}),
         @UniqueConstraint(name = "uq_creation_step_dispatch_attempts_provider_request_key", columnNames = "provider_request_key")
   },
   indexes = {
         @Index(name = "idx_creation_step_dispatch_attempts_step", columnList = "creation_step_id, id"),
         @Index(name = "idx_creation_step_dispatch_attempts_execution", columnList = "creation_execution_attempt_id, id")
   }
)
public class CreationStepDispatchAttempt {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "creation_step_id", nullable = false, foreignKey = @ForeignKey(name = "fk_creation_step_dispatch_attempts_step"))
   private CreationStep creationStep;
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(
      name = "creation_execution_attempt_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_creation_step_dispatch_attempts_execution_attempt")
   )
   private CreationExecutionAttempt creationExecutionAttempt;
   @Column(name = "provider_request_key", length = 128)
   private String providerRequestKey;
   @Column(name = "dispatch_state", nullable = false, length = 32)
   private String dispatchState;
   @Column(name = "dispatch_started_at")
   private LocalDateTime dispatchStartedAt;
   @Column(name = "finished_at")
   private LocalDateTime finishedAt;
   @Column(name = "resolution_code", length = 128)
   private String resolutionCode;
   @ManyToOne(fetch = FetchType.LAZY, optional = true)
   @JoinColumn(name = "result_asset_id", foreignKey = @ForeignKey(name = "fk_creation_step_dispatch_attempts_result_asset"))
   private MediaAsset resultAsset;
   @Column(name = "result_digest", length = 64)
   private String resultDigest;
   @Column(name = "canonical_poem_digest", length = 64)
   private String canonicalPoemDigest;

   public static CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder builder() {
      return new CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder();
   }

   public Long getId() {
      return this.id;
   }

   public CreationStep getCreationStep() {
      return this.creationStep;
   }

   public CreationExecutionAttempt getCreationExecutionAttempt() {
      return this.creationExecutionAttempt;
   }

   public String getProviderRequestKey() {
      return this.providerRequestKey;
   }

   public String getDispatchState() {
      return this.dispatchState;
   }

   public LocalDateTime getDispatchStartedAt() {
      return this.dispatchStartedAt;
   }

   public LocalDateTime getFinishedAt() {
      return this.finishedAt;
   }

   public String getResolutionCode() {
      return this.resolutionCode;
   }

   public MediaAsset getResultAsset() {
      return this.resultAsset;
   }

   public String getResultDigest() {
      return this.resultDigest;
   }

   public String getCanonicalPoemDigest() {
      return this.canonicalPoemDigest;
   }

   public void setId(final Long id) {
      this.id = id;
   }

   public void setCreationStep(final CreationStep creationStep) {
      this.creationStep = creationStep;
   }

   public void setCreationExecutionAttempt(final CreationExecutionAttempt creationExecutionAttempt) {
      this.creationExecutionAttempt = creationExecutionAttempt;
   }

   public void setProviderRequestKey(final String providerRequestKey) {
      this.providerRequestKey = providerRequestKey;
   }

   public void setDispatchState(final String dispatchState) {
      this.dispatchState = dispatchState;
   }

   public void setDispatchStartedAt(final LocalDateTime dispatchStartedAt) {
      this.dispatchStartedAt = dispatchStartedAt;
   }

   public void setFinishedAt(final LocalDateTime finishedAt) {
      this.finishedAt = finishedAt;
   }

   public void setResolutionCode(final String resolutionCode) {
      this.resolutionCode = resolutionCode;
   }

   public void setResultAsset(final MediaAsset resultAsset) {
      this.resultAsset = resultAsset;
   }

   public void setResultDigest(final String resultDigest) {
      this.resultDigest = resultDigest;
   }

   public void setCanonicalPoemDigest(final String canonicalPoemDigest) {
      this.canonicalPoemDigest = canonicalPoemDigest;
   }

   public CreationStepDispatchAttempt() {
   }

   public CreationStepDispatchAttempt(
      final Long id,
      final CreationStep creationStep,
      final CreationExecutionAttempt creationExecutionAttempt,
      final String providerRequestKey,
      final String dispatchState,
      final LocalDateTime dispatchStartedAt,
      final LocalDateTime finishedAt,
      final String resolutionCode,
      final MediaAsset resultAsset,
      final String resultDigest,
      final String canonicalPoemDigest
   ) {
      this.id = id;
      this.creationStep = creationStep;
      this.creationExecutionAttempt = creationExecutionAttempt;
      this.providerRequestKey = providerRequestKey;
      this.dispatchState = dispatchState;
      this.dispatchStartedAt = dispatchStartedAt;
      this.finishedAt = finishedAt;
      this.resolutionCode = resolutionCode;
      this.resultAsset = resultAsset;
      this.resultDigest = resultDigest;
      this.canonicalPoemDigest = canonicalPoemDigest;
   }

   public static class CreationStepDispatchAttemptBuilder {
      private Long id;
      private CreationStep creationStep;
      private CreationExecutionAttempt creationExecutionAttempt;
      private String providerRequestKey;
      private String dispatchState;
      private LocalDateTime dispatchStartedAt;
      private LocalDateTime finishedAt;
      private String resolutionCode;
      private MediaAsset resultAsset;
      private String resultDigest;
      private String canonicalPoemDigest;

      CreationStepDispatchAttemptBuilder() {
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder id(final Long id) {
         this.id = id;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder creationStep(final CreationStep creationStep) {
         this.creationStep = creationStep;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder creationExecutionAttempt(final CreationExecutionAttempt creationExecutionAttempt) {
         this.creationExecutionAttempt = creationExecutionAttempt;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder providerRequestKey(final String providerRequestKey) {
         this.providerRequestKey = providerRequestKey;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder dispatchState(final String dispatchState) {
         this.dispatchState = dispatchState;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder dispatchStartedAt(final LocalDateTime dispatchStartedAt) {
         this.dispatchStartedAt = dispatchStartedAt;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder finishedAt(final LocalDateTime finishedAt) {
         this.finishedAt = finishedAt;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder resolutionCode(final String resolutionCode) {
         this.resolutionCode = resolutionCode;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder resultAsset(final MediaAsset resultAsset) {
         this.resultAsset = resultAsset;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder resultDigest(final String resultDigest) {
         this.resultDigest = resultDigest;
         return this;
      }

      public CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder canonicalPoemDigest(final String canonicalPoemDigest) {
         this.canonicalPoemDigest = canonicalPoemDigest;
         return this;
      }

      public CreationStepDispatchAttempt build() {
         return new CreationStepDispatchAttempt(
            this.id,
            this.creationStep,
            this.creationExecutionAttempt,
            this.providerRequestKey,
            this.dispatchState,
            this.dispatchStartedAt,
            this.finishedAt,
            this.resolutionCode,
            this.resultAsset,
            this.resultDigest,
            this.canonicalPoemDigest
         );
      }

      @Override
      public String toString() {
         return "CreationStepDispatchAttempt.CreationStepDispatchAttemptBuilder(id="
            + this.id
            + ", creationStep="
            + this.creationStep
            + ", creationExecutionAttempt="
            + this.creationExecutionAttempt
            + ", providerRequestKey="
            + this.providerRequestKey
            + ", dispatchState="
            + this.dispatchState
            + ", dispatchStartedAt="
            + this.dispatchStartedAt
            + ", finishedAt="
            + this.finishedAt
            + ", resolutionCode="
            + this.resolutionCode
            + ", resultAsset="
            + this.resultAsset
            + ", resultDigest="
            + this.resultDigest
            + ", canonicalPoemDigest="
            + this.canonicalPoemDigest
            + ")";
      }
   }
}

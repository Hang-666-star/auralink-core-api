package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "creation_steps",
   uniqueConstraints = {
         @UniqueConstraint(name = "uq_creation_steps_public_id", columnNames = "public_id"),
         @UniqueConstraint(name = "uq_creation_steps_creation_index", columnNames = {"creation_id", "step_index"})
   },
   indexes = {
         @Index(name = "idx_creation_steps_creation_status", columnList = "creation_id, status"),
         @Index(name = "idx_creation_steps_input_asset", columnList = "input_asset_id"),
         @Index(name = "idx_creation_steps_output_asset", columnList = "output_asset_id")
   }
)
public class CreationStep extends BasePublicIdEntity {
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "creation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_creation_steps_creation"))
   private Creation creation;
   @Column(name = "step_index", nullable = false)
   private int stepIndex;
   @Column(name = "node_id", nullable = false, length = 255)
   private String nodeId;
   @Column(name = "operation_code", nullable = false, length = 128)
   private String operationCode;
   @Column(name = "provider_code", length = 128)
   private String providerCode;
   @Column(name = "input_modality", nullable = false, length = 64)
   private String inputModality;
   @Column(name = "output_modality", nullable = false, length = 64)
   private String outputModality;
   @Column(name = "input_json", columnDefinition = "TEXT")
   private String inputJson;
   @Column(name = "parameters_json", columnDefinition = "TEXT")
   private String parametersJson;
   @Column(name = "output_json", columnDefinition = "TEXT")
   private String outputJson;
   @ManyToOne(fetch = FetchType.LAZY, optional = true)
   @JoinColumn(name = "input_asset_id", foreignKey = @ForeignKey(name = "fk_creation_steps_input_asset"))
   private MediaAsset inputAsset;
   @ManyToOne(fetch = FetchType.LAZY, optional = true)
   @JoinColumn(name = "output_asset_id", foreignKey = @ForeignKey(name = "fk_creation_steps_output_asset"))
   private MediaAsset outputAsset;
   @Column(nullable = false, length = 64)
   private String status;
   @Column(name = "attempt_count", nullable = false)
   private int attemptCount;
   @Column(name = "error_code", length = 128)
   private String errorCode;
   @Column(name = "error_message", columnDefinition = "TEXT")
   private String errorMessage;
   @Column(name = "started_at")
   private LocalDateTime startedAt;
   @Column(name = "finished_at")
   private LocalDateTime finishedAt;
   @Column(name = "provider_dispatch_state", nullable = false, length = 32)
   private String providerDispatchState;
   @Column(name = "provider_request_key", length = 128)
   private String providerRequestKey;

   private static int $default$attemptCount() {
      return 0;
   }

   private static String $default$providerDispatchState() {
      return "NOT_SENT";
   }

   protected CreationStep(final CreationStep.CreationStepBuilder<?, ?> b) {
      super(b);
      this.creation = b.creation;
      this.stepIndex = b.stepIndex;
      this.nodeId = b.nodeId;
      this.operationCode = b.operationCode;
      this.providerCode = b.providerCode;
      this.inputModality = b.inputModality;
      this.outputModality = b.outputModality;
      this.inputJson = b.inputJson;
      this.parametersJson = b.parametersJson;
      this.outputJson = b.outputJson;
      this.inputAsset = b.inputAsset;
      this.outputAsset = b.outputAsset;
      this.status = b.status;
      if (b.attemptCount$set) {
         this.attemptCount = b.attemptCount$value;
      } else {
         this.attemptCount = $default$attemptCount();
      }

      this.errorCode = b.errorCode;
      this.errorMessage = b.errorMessage;
      this.startedAt = b.startedAt;
      this.finishedAt = b.finishedAt;
      if (b.providerDispatchState$set) {
         this.providerDispatchState = b.providerDispatchState$value;
      } else {
         this.providerDispatchState = $default$providerDispatchState();
      }

      this.providerRequestKey = b.providerRequestKey;
   }

   public static CreationStep.CreationStepBuilder<?, ?> builder() {
      return new CreationStep.CreationStepBuilderImpl();
   }

   public Creation getCreation() {
      return this.creation;
   }

   public int getStepIndex() {
      return this.stepIndex;
   }

   public String getNodeId() {
      return this.nodeId;
   }

   public String getOperationCode() {
      return this.operationCode;
   }

   public String getProviderCode() {
      return this.providerCode;
   }

   public String getInputModality() {
      return this.inputModality;
   }

   public String getOutputModality() {
      return this.outputModality;
   }

   public String getInputJson() {
      return this.inputJson;
   }

   public String getParametersJson() {
      return this.parametersJson;
   }

   public String getOutputJson() {
      return this.outputJson;
   }

   public MediaAsset getInputAsset() {
      return this.inputAsset;
   }

   public MediaAsset getOutputAsset() {
      return this.outputAsset;
   }

   public String getStatus() {
      return this.status;
   }

   public int getAttemptCount() {
      return this.attemptCount;
   }

   public String getErrorCode() {
      return this.errorCode;
   }

   public String getErrorMessage() {
      return this.errorMessage;
   }

   public LocalDateTime getStartedAt() {
      return this.startedAt;
   }

   public LocalDateTime getFinishedAt() {
      return this.finishedAt;
   }

   public String getProviderDispatchState() {
      return this.providerDispatchState;
   }

   public String getProviderRequestKey() {
      return this.providerRequestKey;
   }

   public void setCreation(final Creation creation) {
      this.creation = creation;
   }

   public void setStepIndex(final int stepIndex) {
      this.stepIndex = stepIndex;
   }

   public void setNodeId(final String nodeId) {
      this.nodeId = nodeId;
   }

   public void setOperationCode(final String operationCode) {
      this.operationCode = operationCode;
   }

   public void setProviderCode(final String providerCode) {
      this.providerCode = providerCode;
   }

   public void setInputModality(final String inputModality) {
      this.inputModality = inputModality;
   }

   public void setOutputModality(final String outputModality) {
      this.outputModality = outputModality;
   }

   public void setInputJson(final String inputJson) {
      this.inputJson = inputJson;
   }

   public void setParametersJson(final String parametersJson) {
      this.parametersJson = parametersJson;
   }

   public void setOutputJson(final String outputJson) {
      this.outputJson = outputJson;
   }

   public void setInputAsset(final MediaAsset inputAsset) {
      this.inputAsset = inputAsset;
   }

   public void setOutputAsset(final MediaAsset outputAsset) {
      this.outputAsset = outputAsset;
   }

   public void setStatus(final String status) {
      this.status = status;
   }

   public void setAttemptCount(final int attemptCount) {
      this.attemptCount = attemptCount;
   }

   public void setErrorCode(final String errorCode) {
      this.errorCode = errorCode;
   }

   public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public void setStartedAt(final LocalDateTime startedAt) {
      this.startedAt = startedAt;
   }

   public void setFinishedAt(final LocalDateTime finishedAt) {
      this.finishedAt = finishedAt;
   }

   public void setProviderDispatchState(final String providerDispatchState) {
      this.providerDispatchState = providerDispatchState;
   }

   public void setProviderRequestKey(final String providerRequestKey) {
      this.providerRequestKey = providerRequestKey;
   }

   public CreationStep() {
      this.attemptCount = $default$attemptCount();
      this.providerDispatchState = $default$providerDispatchState();
   }

   public CreationStep(
      final Creation creation,
      final int stepIndex,
      final String nodeId,
      final String operationCode,
      final String providerCode,
      final String inputModality,
      final String outputModality,
      final String inputJson,
      final String parametersJson,
      final String outputJson,
      final MediaAsset inputAsset,
      final MediaAsset outputAsset,
      final String status,
      final int attemptCount,
      final String errorCode,
      final String errorMessage,
      final LocalDateTime startedAt,
      final LocalDateTime finishedAt,
      final String providerDispatchState,
      final String providerRequestKey
   ) {
      this.creation = creation;
      this.stepIndex = stepIndex;
      this.nodeId = nodeId;
      this.operationCode = operationCode;
      this.providerCode = providerCode;
      this.inputModality = inputModality;
      this.outputModality = outputModality;
      this.inputJson = inputJson;
      this.parametersJson = parametersJson;
      this.outputJson = outputJson;
      this.inputAsset = inputAsset;
      this.outputAsset = outputAsset;
      this.status = status;
      this.attemptCount = attemptCount;
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
      this.startedAt = startedAt;
      this.finishedAt = finishedAt;
      this.providerDispatchState = providerDispatchState;
      this.providerRequestKey = providerRequestKey;
   }

   public abstract static class CreationStepBuilder<C extends CreationStep, B extends CreationStep.CreationStepBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private Creation creation;
      private int stepIndex;
      private String nodeId;
      private String operationCode;
      private String providerCode;
      private String inputModality;
      private String outputModality;
      private String inputJson;
      private String parametersJson;
      private String outputJson;
      private MediaAsset inputAsset;
      private MediaAsset outputAsset;
      private String status;
      private boolean attemptCount$set;
      private int attemptCount$value;
      private String errorCode;
      private String errorMessage;
      private LocalDateTime startedAt;
      private LocalDateTime finishedAt;
      private boolean providerDispatchState$set;
      private String providerDispatchState$value;
      private String providerRequestKey;

      public B creation(final Creation creation) {
         this.creation = creation;
         return this.self();
      }

      public B stepIndex(final int stepIndex) {
         this.stepIndex = stepIndex;
         return this.self();
      }

      public B nodeId(final String nodeId) {
         this.nodeId = nodeId;
         return this.self();
      }

      public B operationCode(final String operationCode) {
         this.operationCode = operationCode;
         return this.self();
      }

      public B providerCode(final String providerCode) {
         this.providerCode = providerCode;
         return this.self();
      }

      public B inputModality(final String inputModality) {
         this.inputModality = inputModality;
         return this.self();
      }

      public B outputModality(final String outputModality) {
         this.outputModality = outputModality;
         return this.self();
      }

      public B inputJson(final String inputJson) {
         this.inputJson = inputJson;
         return this.self();
      }

      public B parametersJson(final String parametersJson) {
         this.parametersJson = parametersJson;
         return this.self();
      }

      public B outputJson(final String outputJson) {
         this.outputJson = outputJson;
         return this.self();
      }

      public B inputAsset(final MediaAsset inputAsset) {
         this.inputAsset = inputAsset;
         return this.self();
      }

      public B outputAsset(final MediaAsset outputAsset) {
         this.outputAsset = outputAsset;
         return this.self();
      }

      public B status(final String status) {
         this.status = status;
         return this.self();
      }

      public B attemptCount(final int attemptCount) {
         this.attemptCount$value = attemptCount;
         this.attemptCount$set = true;
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

      public B startedAt(final LocalDateTime startedAt) {
         this.startedAt = startedAt;
         return this.self();
      }

      public B finishedAt(final LocalDateTime finishedAt) {
         this.finishedAt = finishedAt;
         return this.self();
      }

      public B providerDispatchState(final String providerDispatchState) {
         this.providerDispatchState$value = providerDispatchState;
         this.providerDispatchState$set = true;
         return this.self();
      }

      public B providerRequestKey(final String providerRequestKey) {
         this.providerRequestKey = providerRequestKey;
         return this.self();
      }

      protected abstract B self();

      public abstract C build();

      @Override
      public String toString() {
         return "CreationStep.CreationStepBuilder(super="
            + super.toString()
            + ", creation="
            + this.creation
            + ", stepIndex="
            + this.stepIndex
            + ", nodeId="
            + this.nodeId
            + ", operationCode="
            + this.operationCode
            + ", providerCode="
            + this.providerCode
            + ", inputModality="
            + this.inputModality
            + ", outputModality="
            + this.outputModality
            + ", inputJson="
            + this.inputJson
            + ", parametersJson="
            + this.parametersJson
            + ", outputJson="
            + this.outputJson
            + ", inputAsset="
            + this.inputAsset
            + ", outputAsset="
            + this.outputAsset
            + ", status="
            + this.status
            + ", attemptCount$value="
            + this.attemptCount$value
            + ", errorCode="
            + this.errorCode
            + ", errorMessage="
            + this.errorMessage
            + ", startedAt="
            + this.startedAt
            + ", finishedAt="
            + this.finishedAt
            + ", providerDispatchState$value="
            + this.providerDispatchState$value
            + ", providerRequestKey="
            + this.providerRequestKey
            + ")";
      }
   }

   private static final class CreationStepBuilderImpl extends CreationStep.CreationStepBuilder<CreationStep, CreationStep.CreationStepBuilderImpl> {
      protected CreationStep.CreationStepBuilderImpl self() {
         return this;
      }

      @Override
      public CreationStep build() {
         return new CreationStep(this);
      }
   }
}

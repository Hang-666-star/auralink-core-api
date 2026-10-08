package com.auralink.creation;

import com.auralink.api.v1.creation.CreationDetailResponse;
import com.auralink.api.v1.creation.CreationPoemResponse;
import com.auralink.api.v1.creation.CreationStepSummaryResponse;
import com.auralink.api.v1.creation.CreationSummaryResponse;
import com.auralink.api.v1.creation.CreationTimestampFormatter;
import com.auralink.entity.Creation;
import com.auralink.entity.CreationStep;
import com.auralink.entity.MediaAsset;
import com.auralink.provider.qwen.PaintingPoemResult;
import com.auralink.provider.qwen.PaintingPoemResultValidator;
import com.auralink.repository.CreationExecutionAttemptRepository;
import com.auralink.repository.CreationStepRepository;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.snapshot.WorkflowSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CreationResponseMapper {
   private final ObjectMapper objectMapper;
   private final PaintingPoemResultValidator poemValidator;
   private final CreationStepRepository steps;
   private final CreationExecutionAttemptRepository executionAttempts;
   private final CreationRetryEligibilityService retryEligibility;

   public CreationResponseMapper(
      ObjectMapper objectMapper,
      PaintingPoemResultValidator poemValidator,
      CreationStepRepository steps,
      CreationExecutionAttemptRepository executionAttempts,
      CreationRetryEligibilityService retryEligibility
   ) {
      this.objectMapper = objectMapper;
      this.poemValidator = poemValidator;
      this.steps = steps;
      this.executionAttempts = executionAttempts;
      this.retryEligibility = retryEligibility;
   }

   public CreationSummaryResponse summary(Creation creation) {
      return this.summary(creation, this.steps.findByCreationIdOrderByStepIndexAsc(creation.getId()));
   }

   private CreationSummaryResponse summary(Creation creation, List<CreationStep> creationSteps) {
      CreationRetryEligibilityService.RetryAssessment retry = this.retryEligibility.assess(creation, creationSteps);
      return new CreationSummaryResponse(
         creation.getPublicId(),
         creation.getWorkflow() == null ? null : creation.getWorkflow().getPublicId(),
         this.workflowName(creation),
         CreationStatus.valueOf(creation.getStatus()),
         creation.getErrorCode(),
         creation.getErrorMessage(),
         this.recoveryState(creation.getErrorCode()),
         WorkflowModality.valueOf(creation.getSourceModality()),
         creation.getSourcePainting() == null ? null : creation.getSourcePainting().getPublicId(),
         creation.getSourceAsset() == null ? null : creation.getSourceAsset().getPublicId(),
         creation.getFinalModality() == null ? null : WorkflowModality.valueOf(creation.getFinalModality()),
         creation.getFinalAsset() == null ? null : creation.getFinalAsset().getPublicId(),
         this.contentUrl(creation.getFinalAsset()),
         this.downloadUrl(creation.getFinalAsset()),
         CreationTimestampFormatter.format(creation.getCreatedAt()),
         CreationTimestampFormatter.format(creation.getUpdatedAt()),
         CreationTimestampFormatter.format(creation.getStartedAt()),
         CreationTimestampFormatter.format(creation.getFinishedAt()),
         creation.getRetryVersion(),
         retry.available(),
         retry.blockedReason(),
         this.executionAttempts.countByCreationId(creation.getId())
      );
   }

   public CreationDetailResponse detail(Creation creation, List<CreationStep> steps) {
      CreationSummaryResponse summary = this.summary(creation, steps);
      CreationPoemResponse poem = this.finalPoem(creation);
      return new CreationDetailResponse(
         summary.creationId(),
         summary.workflowId(),
         summary.workflowName(),
         summary.status(),
         summary.errorCode(),
         summary.errorMessage(),
         summary.recoveryState(),
         summary.sourceModality(),
         summary.sourcePaintingId(),
         creation.getSourcePainting() == null ? null : creation.getSourcePainting().getTitle(),
         creation.getSourcePainting() == null ? null : this.contentUrl(creation.getSourcePainting().getImageAsset()),
         summary.sourceAssetId(),
         creation.getSourceText(),
         this.contentUrl(creation.getSourceAsset()),
         this.downloadUrl(creation.getSourceAsset()),
         summary.finalModality(),
         summary.finalAssetId(),
         summary.finalAssetContentUrl(),
         summary.finalAssetDownloadUrl(),
         poem == null ? null : poem.text(),
         poem,
         summary.createdAt(),
         summary.updatedAt(),
         summary.startedAt(),
         summary.finishedAt(),
         summary.retryVersion(),
         summary.retryAvailable(),
         summary.retryBlockedReason(),
         summary.executionAttemptCount(),
         steps.stream().map(this::step).toList()
      );
   }

   private CreationStepSummaryResponse step(CreationStep step) {
      CreationPoemResponse poem = this.stepPoem(step);
      return new CreationStepSummaryResponse(
         step.getPublicId(),
         step.getStepIndex(),
         step.getNodeId(),
         WorkflowOperation.valueOf(step.getOperationCode()),
         WorkflowModality.valueOf(step.getInputModality()),
         WorkflowModality.valueOf(step.getOutputModality()),
         CreationStepStatus.valueOf(step.getStatus()),
         step.getAttemptCount(),
         step.getErrorCode(),
         step.getErrorMessage(),
         step.getOutputAsset() == null ? null : step.getOutputAsset().getPublicId(),
         this.contentUrl(step.getOutputAsset()),
         this.downloadUrl(step.getOutputAsset()),
         poem == null ? null : poem.text(),
         poem,
         this.requestedDurationSeconds(step),
         CreationTimestampFormatter.format(step.getStartedAt()),
         CreationTimestampFormatter.format(step.getFinishedAt())
      );
   }

   private String workflowName(Creation creation) {
      try {
         return ((WorkflowSnapshot)this.objectMapper.readValue(creation.getWorkflowSnapshot(), WorkflowSnapshot.class)).workflowName();
      } catch (Exception exception) {
         return null;
      }
   }

   private CreationPoemResponse finalPoem(Creation creation) {
      if (creation.getFinalOutputJson() != null && WorkflowModality.POEM.name().equals(creation.getFinalModality())) {
         try {
            PaintingPoemResult poem = this.poemValidator.validate(creation.getFinalOutputJson());
            return new CreationPoemResponse(poem.schemaVersion(), poem.title(), poem.lines(), poem.text());
         } catch (RuntimeException exception) {
            return null;
         }
      } else {
         return null;
      }
   }

   private CreationPoemResponse stepPoem(CreationStep step) {
      if (step.getOutputJson() != null && WorkflowModality.POEM.name().equals(step.getOutputModality())) {
         try {
            PaintingPoemResult poem = this.poemValidator.validate(step.getOutputJson());
            return new CreationPoemResponse(poem.schemaVersion(), poem.title(), poem.lines(), poem.text());
         } catch (RuntimeException exception) {
            return null;
         }
      } else {
         return null;
      }
   }

   private Integer requestedDurationSeconds(CreationStep step) {
      if (WorkflowOperation.PAINTING_TO_MUSIC.name().equals(step.getOperationCode()) && step.getParametersJson() != null) {
         try {
            JsonNode value = this.objectMapper.readTree(step.getParametersJson()).path("durationSeconds");
            if (value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 3 && value.intValue() <= 30) {
               return value.intValue();
            }
         } catch (Exception var3) {
         }

         return null;
      } else {
         return null;
      }
   }

   private String contentUrl(MediaAsset asset) {
      return asset == null ? null : "/api/v1/assets/" + asset.getPublicId() + "/content";
   }

   private String downloadUrl(MediaAsset asset) {
      return asset == null ? null : "/api/v1/assets/" + asset.getPublicId() + "/download";
   }

   private CreationRecoveryState recoveryState(String errorCode) {
      if ("PROVIDER_DISPATCH_AMBIGUOUS".equals(errorCode)) {
         return CreationRecoveryState.PROVIDER_DISPATCH_AMBIGUOUS;
      } else {
         return !"CREATION_STATE_INCONSISTENT".equals(errorCode) && !"CREATION_RESULT_PERSISTENCE_INCONSISTENT".equals(errorCode)
            ? CreationRecoveryState.NONE
            : CreationRecoveryState.OPERATOR_REVIEW_REQUIRED;
      }
   }
}

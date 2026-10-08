package com.auralink.creation;

import com.auralink.config.properties.CreationExecutionProperties;
import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.entity.Creation;
import com.auralink.entity.CreationStep;
import com.auralink.entity.CreationStepDispatchAttempt;
import com.auralink.entity.MediaAsset;
import com.auralink.entity.Painting;
import com.auralink.provider.qwen.PaintingPoemResultValidator;
import com.auralink.repository.CreationExecutionAttemptRepository;
import com.auralink.repository.CreationStepDispatchAttemptRepository;
import com.auralink.service.media.MediaAssetStorageService;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowNodeKind;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.capability.WorkflowCapabilityRegistry;
import com.auralink.workflow.capability.WorkflowOperationCapability;
import com.auralink.workflow.graph.CanonicalWorkflowNode;
import com.auralink.workflow.snapshot.WorkflowSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CreationRetryEligibilityService {
   static final String NOT_AVAILABLE = "CREATION_RETRY_NOT_AVAILABLE";
   static final String AMBIGUOUS = "CREATION_RETRY_DISPATCH_AMBIGUOUS";
   static final String INCONSISTENT = "CREATION_DATA_INCONSISTENT";
   static final String FEATURE_DISABLED = "CREATIONS_FEATURE_DISABLED";
   private final CreationExecutionProperties executionProperties;
   private final CreationProviderProperties providerProperties;
   private final CreationExecutionCapabilityService capabilities;
   private final WorkflowCapabilityRegistry workflowCapabilities;
   private final CreationExecutionAttemptRepository executionAttempts;
   private final CreationStepDispatchAttemptRepository dispatchAttempts;
   private final PaintingPoemResultValidator poemValidator;
   private final MediaAssetStorageService mediaStorage;
   private final ObjectMapper objectMapper;

   public CreationRetryEligibilityService.RetryAssessment assess(Creation creation, List<CreationStep> steps) {
      if (creation != null && steps != null) {
         if (!CreationStatus.FAILED.name().equals(creation.getStatus()) && !CreationStatus.PARTIAL_SUCCESS.name().equals(creation.getStatus())) {
            return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_RETRY_NOT_AVAILABLE");
         }

         if (creation.getClaimToken() == null
            && creation.getLeaseExpiresAt() == null
            && !this.executionAttempts.findByCreationIdAndFinishedAtIsNull(creation.getId()).isPresent()) {
            try {
               List<CreationRetryEligibilityService.SnapshotStep> snapshot = this.parseLinearSnapshot(creation.getWorkflowSnapshot());
               this.capabilities
                  .requireExecutionAvailable(
                     snapshot.stream().map(CreationRetryEligibilityService.SnapshotStep::operation).toList(),
                     creation.getSourceModality(),
                     creation.getSourcePainting() == null ? null : creation.getSourcePainting().getId(),
                     creation.getSourceAsset() == null ? null : creation.getSourceAsset().getId()
                  );
               int boundary = this.requireConsistentSequence(creation, snapshot, steps);
               this.validateSource(creation, snapshot.get(0));

               for (int index = 0; index < boundary; index++) {
                  this.validateSuccessfulOutput(creation, snapshot.get(index), steps.get(index));
               }

               CreationStep boundaryStep = steps.get(boundary);
               List<CreationStepDispatchAttempt> history = this.dispatchAttempts.findByCreationStepIdOrderByIdAsc(boundaryStep.getId());
               if ((!ProviderDispatchState.NOT_SENT.name().equals(boundaryStep.getProviderDispatchState()) || boundaryStep.getProviderRequestKey() == null)
                  && !history.stream()
                     .anyMatch(attempt -> ProviderDispatchState.NOT_SENT.name().equals(attempt.getDispatchState()) && attempt.getProviderRequestKey() != null)) {
                  if (history.stream().anyMatch(attempt -> ProviderDispatchState.RESULT_PERSISTED.name().equals(attempt.getDispatchState()))) {
                     return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_DATA_INCONSISTENT");
                  }

                  if (history.stream().anyMatch(attempt -> ProviderDispatchState.SEND_STARTED.name().equals(attempt.getDispatchState()))
                     || ProviderDispatchState.SEND_STARTED.name().equals(boundaryStep.getProviderDispatchState())) {
                     return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_RETRY_DISPATCH_AMBIGUOUS");
                  }

                  if (!this.executionProperties.isEnabled()) {
                     return CreationRetryEligibilityService.RetryAssessment.blocked("CREATIONS_FEATURE_DISABLED");
                  }

                  for (CreationRetryEligibilityService.SnapshotStep step : snapshot) {
                     WorkflowOperationCapability capability = this.workflowCapabilities.require(step.operation());
                     if (!capability.definitionEnabled()
                        || !capability.allowsProvider(step.providerCode())
                        || capability.inputModality() != step.input()
                        || capability.outputModality() != step.output()) {
                        return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_OPERATION_UNAVAILABLE");
                     }

                     CreationExecutionCapabilityService.ExecutionAvailability availability = this.capabilities
                        .availability(step.operation(), step.providerCode());
                     if (!availability.available()) {
                        return CreationRetryEligibilityService.RetryAssessment.blocked(availability.reason());
                     }
                  }

                  return CreationRetryEligibilityService.RetryAssessment.available(boundary);
               } else {
                  return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_DATA_INCONSISTENT");
               }
            } catch (RuntimeException exception) {
               return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_DATA_INCONSISTENT");
            }
         } else {
            return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_DATA_INCONSISTENT");
         }
      } else {
         return CreationRetryEligibilityService.RetryAssessment.blocked("CREATION_DATA_INCONSISTENT");
      }
   }

   private List<CreationRetryEligibilityService.SnapshotStep> parseLinearSnapshot(String raw) {
      try {
         WorkflowSnapshot snapshot = (WorkflowSnapshot)this.objectMapper.readValue(raw, WorkflowSnapshot.class);
         if (snapshot != null
            && snapshot.snapshotVersion() == 1
            && snapshot.graph() != null
            && snapshot.graph().nodes() != null
            && snapshot.graph().edges() != null) {
            List<CanonicalWorkflowNode> sources = snapshot.graph().nodes().stream().filter(nodex -> nodex.kind() == WorkflowNodeKind.SOURCE).toList();
            List<CanonicalWorkflowNode> transforms = snapshot.graph().nodes().stream().filter(nodex -> nodex.kind() == WorkflowNodeKind.TRANSFORM).toList();
            if (sources.size() == 1 && !transforms.isEmpty()) {
               String previous = sources.get(0).id();

               for (CanonicalWorkflowNode node : transforms) {
                  String expectedPrevious = previous;
                  if (node.id() == null
                     || node.operation() == null
                     || node.providerCode() == null
                     || node.inputModality() == null
                     || node.outputModality() == null
                     || snapshot.graph().edges().stream().filter(edge -> expectedPrevious.equals(edge.from()) && node.id().equals(edge.to())).count() != 1L) {
                     throw new CreationRetryEligibilityService.RetryInconsistencyException();
                  }

                  previous = node.id();
               }

               if (snapshot.graph().edges().size() != transforms.size()) {
                  throw new CreationRetryEligibilityService.RetryInconsistencyException();
               } else {
                  return transforms.stream()
                     .map(
                        nodex -> new CreationRetryEligibilityService.SnapshotStep(
                           nodex.id(), nodex.operation(), nodex.providerCode(), nodex.inputModality(), nodex.outputModality()
                        )
                     )
                     .toList();
               }
            } else {
               throw new CreationRetryEligibilityService.RetryInconsistencyException();
            }
         } else {
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
         }
      } catch (CreationRetryEligibilityService.RetryInconsistencyException exception) {
         throw exception;
      } catch (Exception exception) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   private int requireConsistentSequence(Creation creation, List<CreationRetryEligibilityService.SnapshotStep> snapshot, List<CreationStep> steps) {
      if (steps.size() != snapshot.size()) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }

      int boundary = -1;
      boolean nonSuccessSeen = false;
      int failedCount = 0;
      int runningCount = 0;
      int succeededCount = 0;

      for (int index = 0; index < steps.size(); index++) {
         CreationStep step = steps.get(index);
         CreationRetryEligibilityService.SnapshotStep expected = snapshot.get(index);
         if (step.getStepIndex() != index
            || !expected.nodeId().equals(step.getNodeId())
            || !expected.operation().name().equals(step.getOperationCode())
            || !expected.providerCode().equals(step.getProviderCode())
            || !expected.input().name().equals(step.getInputModality())
            || !expected.output().name().equals(step.getOutputModality())) {
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
         }

         String status = step.getStatus();
         if (CreationStepStatus.SUCCEEDED.name().equals(status)) {
            if (nonSuccessSeen) {
               throw new CreationRetryEligibilityService.RetryInconsistencyException();
            }

            succeededCount++;
         } else {
            nonSuccessSeen = true;
            if (CreationStepStatus.FAILED.name().equals(status)) {
               failedCount++;
               if (boundary >= 0) {
                  throw new CreationRetryEligibilityService.RetryInconsistencyException();
               }

               boundary = index;
            } else {
               if (!CreationStepStatus.PENDING.name().equals(status)) {
                  if (CreationStepStatus.RUNNING.name().equals(status)) {
                     runningCount++;
                     throw new CreationRetryEligibilityService.RetryInconsistencyException();
                  }

                  throw new CreationRetryEligibilityService.RetryInconsistencyException();
               }

               if (boundary < 0) {
                  boundary = index;
               }

               if (ProviderDispatchState.SEND_STARTED.name().equals(step.getProviderDispatchState())
                  || ProviderDispatchState.RESULT_PERSISTED.name().equals(step.getProviderDispatchState())
                  || step.getProviderRequestKey() != null) {
                  throw new CreationRetryEligibilityService.RetryInconsistencyException();
               }
            }
         }
      }

      if (runningCount != 0 || boundary < 0) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }

      if (CreationStatus.PARTIAL_SUCCESS.name().equals(creation.getStatus()) && (failedCount != 1 || succeededCount == 0)) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }

      if (!CreationStatus.FAILED.name().equals(creation.getStatus()) || failedCount <= 1 && succeededCount == 0) {
         CreationStep boundaryStep = steps.get(boundary);
         if (CreationStepStatus.FAILED.name().equals(boundaryStep.getStatus())
            && ProviderDispatchState.RESULT_PERSISTED.name().equals(boundaryStep.getProviderDispatchState())) {
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
         } else {
            return boundary;
         }
      } else {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   private void validateSource(Creation creation, CreationRetryEligibilityService.SnapshotStep first) {
      WorkflowModality source = this.parseModality(creation.getSourceModality());
      if (source != first.input()) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }

      switch (source) {
         case TEXT_DESCRIPTION:
         case POEM:
            String text = creation.getSourceText();
            if (text == null
               || text.isBlank()
               || text.length() > this.providerProperties.getMaxTextChars()
               || text.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint) && codePoint != 10 && codePoint != 13 && codePoint != 9)) {
               throw new CreationRetryEligibilityService.RetryInconsistencyException();
            }
            break;
         case IMAGE:
            this.requireOwnedImage(creation.getSourceAsset(), creation.getUser().getId(), false);
            break;
         case PAINTING:
            Painting painting = creation.getSourcePainting();
            if (painting == null || !"ACTIVE".equals(painting.getStatus()) || !painting.isImageAvailable()) {
               throw new CreationRetryEligibilityService.RetryInconsistencyException();
            }

            MediaAsset image = painting.getImageAsset();
            if (image == null
               || image.getOwnerUser() != null
               || !"CATALOG_REFERENCE".equals(image.getSourceType())
               || !"PUBLIC".equals(image.getVisibility())
               || !"ACTIVE".equals(image.getStatus())
               || !"IMAGE".equals(image.getAssetType())) {
               throw new CreationRetryEligibilityService.RetryInconsistencyException();
            }

            this.mediaStorage.resolve(image);
            break;
         default:
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   private void validateSuccessfulOutput(Creation creation, CreationRetryEligibilityService.SnapshotStep expected, CreationStep step) {
      if (!CreationStepStatus.SUCCEEDED.name().equals(step.getStatus())
         || !ProviderDispatchState.RESULT_PERSISTED.name().equals(step.getProviderDispatchState())) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }

      if (expected.output() == WorkflowModality.PAINTING) {
         this.requireOwnedImage(step.getOutputAsset(), creation.getUser().getId(), true);
         if (step.getOutputJson() != null) {
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
         }
      } else if (expected.output() == WorkflowModality.POEM) {
         if (step.getOutputAsset() != null) {
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
         }

         this.poemValidator.validate(step.getOutputJson());
      } else if (expected.output() == WorkflowModality.AUDIO) {
         this.requireOwnedAudio(step.getOutputAsset(), creation.getUser().getId());
         if (step.getOutputJson() != null) {
            throw new CreationRetryEligibilityService.RetryInconsistencyException();
         }
      } else {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   private void requireOwnedImage(MediaAsset asset, Long ownerId, boolean generated) {
      if (asset != null
         && ownerId != null
         && asset.getOwnerUser() != null
         && ownerId.equals(asset.getOwnerUser().getId())
         && "ACTIVE".equals(asset.getStatus())
         && "IMAGE".equals(asset.getAssetType())
         && "PRIVATE".equals(asset.getVisibility())
         && (!generated || "GENERATED".equals(asset.getSourceType()) && "GENERATED_PAINTING".equals(asset.getSemanticType()))) {
         this.mediaStorage.resolve(asset);
      } else {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   private void requireOwnedAudio(MediaAsset asset, Long ownerId) {
      if (asset != null
         && ownerId != null
         && asset.getOwnerUser() != null
         && ownerId.equals(asset.getOwnerUser().getId())
         && "ACTIVE".equals(asset.getStatus())
         && "AUDIO".equals(asset.getAssetType())
         && "MUSIC".equals(asset.getSemanticType())
         && "GENERATED".equals(asset.getSourceType())
         && "PRIVATE".equals(asset.getVisibility())) {
         this.mediaStorage.resolve(asset);
      } else {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   private WorkflowModality parseModality(String value) {
      try {
         return WorkflowModality.valueOf(value);
      } catch (RuntimeException exception) {
         throw new CreationRetryEligibilityService.RetryInconsistencyException();
      }
   }

   public CreationRetryEligibilityService(
      final CreationExecutionProperties executionProperties,
      final CreationProviderProperties providerProperties,
      final CreationExecutionCapabilityService capabilities,
      final WorkflowCapabilityRegistry workflowCapabilities,
      final CreationExecutionAttemptRepository executionAttempts,
      final CreationStepDispatchAttemptRepository dispatchAttempts,
      final PaintingPoemResultValidator poemValidator,
      final MediaAssetStorageService mediaStorage,
      final ObjectMapper objectMapper
   ) {
      this.executionProperties = executionProperties;
      this.providerProperties = providerProperties;
      this.capabilities = capabilities;
      this.workflowCapabilities = workflowCapabilities;
      this.executionAttempts = executionAttempts;
      this.dispatchAttempts = dispatchAttempts;
      this.poemValidator = poemValidator;
      this.mediaStorage = mediaStorage;
      this.objectMapper = objectMapper;
   }

   public record RetryAssessment(boolean available, String blockedReason, int boundaryIndex) {
      static CreationRetryEligibilityService.RetryAssessment available(int boundaryIndex) {
         return new CreationRetryEligibilityService.RetryAssessment(true, null, boundaryIndex);
      }

      static CreationRetryEligibilityService.RetryAssessment blocked(String reason) {
         return new CreationRetryEligibilityService.RetryAssessment(false, reason, -1);
      }
   }

   private static final class RetryInconsistencyException extends RuntimeException {
   }

   private record SnapshotStep(String nodeId, WorkflowOperation operation, String providerCode, WorkflowModality input, WorkflowModality output) {
   }
}

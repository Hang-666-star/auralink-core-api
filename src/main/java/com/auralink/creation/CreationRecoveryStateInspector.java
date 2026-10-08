package com.auralink.creation;

import com.auralink.entity.CreationExecutionAttempt;
import com.auralink.entity.CreationStep;
import com.auralink.entity.CreationStepDispatchAttempt;
import com.auralink.provider.qwen.PaintingPoemResultValidator;
import com.auralink.service.media.MediaAssetStorageService;
import com.auralink.workflow.WorkflowModality;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CreationRecoveryStateInspector {
   static final String AMBIGUOUS = "PROVIDER_DISPATCH_AMBIGUOUS";
   static final String INCONSISTENT = "CREATION_STATE_INCONSISTENT";
   static final String RESULT_INCONSISTENT = "CREATION_RESULT_PERSISTENCE_INCONSISTENT";
   private final PaintingPoemResultValidator poemValidator;
   private final MediaAssetStorageService mediaStorage;

   public CreationRecoveryStateInspector(PaintingPoemResultValidator poemValidator, MediaAssetStorageService mediaStorage) {
      this.poemValidator = poemValidator;
      this.mediaStorage = mediaStorage;
   }

   public CreationRecoveryStateInspector.RecoveryDecision inspect(
      List<CreationStep> steps, List<CreationExecutionAttempt> unfinishedAttempts, Map<Long, CreationStepDispatchAttempt> activeDispatchAttempts
   ) {
      if (steps != null && !steps.isEmpty()) {
         boolean invalidActiveAttempt = unfinishedAttempts == null || unfinishedAttempts.size() != 1;
         int running = 0;
         int failed = 0;
         int firstNonSucceeded = -1;
         boolean priorSuccess = false;

         for (int index = 0; index < steps.size(); index++) {
            CreationStep step = steps.get(index);
            if (step.getStepIndex() != index || !this.knownStatus(step.getStatus())) {
               return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
            }

            if (CreationStepStatus.SUCCEEDED.name().equals(step.getStatus())) {
               if (firstNonSucceeded >= 0 || !this.validSucceededOutput(step)) {
                  return CreationRecoveryStateInspector.RecoveryDecision.inconsistent(
                     this.validSucceededOutput(step) ? "CREATION_STATE_INCONSISTENT" : "CREATION_RESULT_PERSISTENCE_INCONSISTENT", priorSuccess
                  );
               }

               priorSuccess = true;
            } else {
               if (firstNonSucceeded < 0) {
                  firstNonSucceeded = index;
               }

               if (CreationStepStatus.RUNNING.name().equals(step.getStatus())) {
                  running++;
               } else if (CreationStepStatus.FAILED.name().equals(step.getStatus())) {
                  failed++;
               }
            }
         }

         if (running > 1 || failed > 1) {
            return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
         }

         if (invalidActiveAttempt) {
            return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
         }

         if (firstNonSucceeded < 0) {
            CreationStep terminal = steps.get(steps.size() - 1);
            return this.validTerminal(terminal)
               ? CreationRecoveryStateInspector.RecoveryDecision.finalizeSuccess(terminal)
               : CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_RESULT_PERSISTENCE_INCONSISTENT", priorSuccess);
         }

         CreationStep boundary = steps.get(firstNonSucceeded);

         for (int index = firstNonSucceeded + 1; index < steps.size(); index++) {
            if (!CreationStepStatus.PENDING.name().equals(steps.get(index).getStatus())) {
               return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
            }

            if (!ProviderDispatchState.NOT_SENT.name().equals(steps.get(index).getProviderDispatchState()) || steps.get(index).getProviderRequestKey() != null) {
               return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
            }
         }

         if (CreationStepStatus.PENDING.name().equals(boundary.getStatus())) {
            return ProviderDispatchState.NOT_SENT.name().equals(boundary.getProviderDispatchState()) && boundary.getProviderRequestKey() == null
               ? CreationRecoveryStateInspector.RecoveryDecision.requeue()
               : CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
         }

         if (CreationStepStatus.FAILED.name().equals(boundary.getStatus())) {
            if (ProviderDispatchState.RESULT_PERSISTED.name().equals(boundary.getProviderDispatchState())) {
               return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_RESULT_PERSISTENCE_INCONSISTENT", priorSuccess);
            }

            String code = ProviderDispatchState.SEND_STARTED.name().equals(boundary.getProviderDispatchState())
               ? "PROVIDER_DISPATCH_AMBIGUOUS"
               : "CREATION_RECOVERY_FINALIZED_FAILED";
            return CreationRecoveryStateInspector.RecoveryDecision.finalizeFailed(boundary, priorSuccess, code);
         } else if (!CreationStepStatus.RUNNING.name().equals(boundary.getStatus()) || running != 1) {
            return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
         } else if (ProviderDispatchState.RESULT_PERSISTED.name().equals(boundary.getProviderDispatchState())) {
            return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_RESULT_PERSISTENCE_INCONSISTENT", priorSuccess);
         } else {
            CreationStepDispatchAttempt dispatch = activeDispatchAttempts.get(boundary.getId());
            if (dispatch == null || !this.same(boundary.getProviderDispatchState(), dispatch.getDispatchState())) {
               return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
            } else if (ProviderDispatchState.NOT_SENT.name().equals(boundary.getProviderDispatchState())) {
               return boundary.getProviderRequestKey() == null && dispatch.getProviderRequestKey() == null
                  ? CreationRecoveryStateInspector.RecoveryDecision.requeueNotSent(boundary)
                  : CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
            } else if (!ProviderDispatchState.SEND_STARTED.name().equals(boundary.getProviderDispatchState())) {
               return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_RESULT_PERSISTENCE_INCONSISTENT", priorSuccess);
            } else {
               return boundary.getProviderRequestKey() != null
                     && dispatch.getProviderRequestKey() != null
                     && boundary.getProviderRequestKey().equals(dispatch.getProviderRequestKey())
                  ? CreationRecoveryStateInspector.RecoveryDecision.ambiguous(boundary, priorSuccess)
                  : CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT", priorSuccess);
            }
         }
      } else {
         return CreationRecoveryStateInspector.RecoveryDecision.inconsistent("CREATION_STATE_INCONSISTENT");
      }
   }

   private boolean knownStatus(String value) {
      return CreationStepStatus.PENDING.name().equals(value)
         || CreationStepStatus.RUNNING.name().equals(value)
         || CreationStepStatus.SUCCEEDED.name().equals(value)
         || CreationStepStatus.FAILED.name().equals(value);
   }

   private boolean validSucceededOutput(CreationStep step) {
      if (!ProviderDispatchState.RESULT_PERSISTED.name().equals(step.getProviderDispatchState())) {
         return false;
      }

      if (!WorkflowModality.PAINTING.name().equals(step.getOutputModality())) {
         if (WorkflowModality.POEM.name().equals(step.getOutputModality())) {
            if (step.getOutputJson() != null && step.getOutputAsset() == null) {
               try {
                  this.poemValidator.validate(step.getOutputJson());
                  return true;
               } catch (RuntimeException exception) {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else if (step.getOutputAsset() != null
         && step.getOutputAsset().getSha256() != null
         && step.getOutputAsset().getFileSize() != null
         && step.getOutputAsset().getFileSize() > 0L
         && ("image/png".equals(step.getOutputAsset().getMimeType()) || "image/jpeg".equals(step.getOutputAsset().getMimeType()))) {
         try {
            MediaAssetStorageService.MediaAssetStoredResource stored = this.mediaStorage.resolve(step.getOutputAsset());
            return stored != null
               && stored.resource() != null
               && stored.resource().exists()
               && stored.resource().isReadable()
               && stored.contentLength() == step.getOutputAsset().getFileSize();
         } catch (RuntimeException exception) {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean validTerminal(CreationStep step) {
      return this.validSucceededOutput(step)
         && (WorkflowModality.PAINTING.name().equals(step.getOutputModality()) || WorkflowModality.POEM.name().equals(step.getOutputModality()));
   }

   private boolean same(String left, String right) {
      return left != null && left.equals(right);
   }

   public enum Kind {
      REQUEUE,
      REQUEUE_NOT_SENT,
      FINALIZE_SUCCESS,
      FINALIZE_FAILED,
      AMBIGUOUS,
      INCONSISTENT;
   }

   public record RecoveryDecision(
      CreationRecoveryStateInspector.Kind kind,
      CreationStep boundary,
      boolean priorSuccess,
      String errorCode,
      String finalModality,
      Long finalAssetId,
      String finalOutputJson
   ) {
      static CreationRecoveryStateInspector.RecoveryDecision requeue() {
         return new CreationRecoveryStateInspector.RecoveryDecision(CreationRecoveryStateInspector.Kind.REQUEUE, null, false, null, null, null, null);
      }

      static CreationRecoveryStateInspector.RecoveryDecision requeueNotSent(CreationStep boundary) {
         return new CreationRecoveryStateInspector.RecoveryDecision(
            CreationRecoveryStateInspector.Kind.REQUEUE_NOT_SENT, boundary, false, null, null, null, null
         );
      }

      static CreationRecoveryStateInspector.RecoveryDecision finalizeSuccess(CreationStep terminal) {
         return new CreationRecoveryStateInspector.RecoveryDecision(
            CreationRecoveryStateInspector.Kind.FINALIZE_SUCCESS,
            terminal,
            true,
            null,
            terminal.getOutputModality(),
            terminal.getOutputAsset() == null ? null : terminal.getOutputAsset().getId(),
            terminal.getOutputJson()
         );
      }

      static CreationRecoveryStateInspector.RecoveryDecision finalizeFailed(CreationStep boundary, boolean priorSuccess, String code) {
         return new CreationRecoveryStateInspector.RecoveryDecision(
            CreationRecoveryStateInspector.Kind.FINALIZE_FAILED, boundary, priorSuccess, code, null, null, null
         );
      }

      static CreationRecoveryStateInspector.RecoveryDecision ambiguous(CreationStep boundary, boolean priorSuccess) {
         return new CreationRecoveryStateInspector.RecoveryDecision(
            CreationRecoveryStateInspector.Kind.AMBIGUOUS, boundary, priorSuccess, "PROVIDER_DISPATCH_AMBIGUOUS", null, null, null
         );
      }

      static CreationRecoveryStateInspector.RecoveryDecision inconsistent(String code) {
         return inconsistent(code, false);
      }

      static CreationRecoveryStateInspector.RecoveryDecision inconsistent(String code, boolean priorSuccess) {
         return new CreationRecoveryStateInspector.RecoveryDecision(
            CreationRecoveryStateInspector.Kind.INCONSISTENT, null, priorSuccess, code, null, null, null
         );
      }
   }
}

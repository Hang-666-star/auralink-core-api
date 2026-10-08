package com.auralink.ops.round9cc;

import com.auralink.creation.CreationExecutionBoundary;
import java.util.LinkedHashMap;
import java.util.Map;

public enum Round9CcScenario {
   NORMAL_COMPLETION(
      null, "DISPATCHER_WORKER", "0", "SUCCEEDED", "SUCCEEDED", "RESULT_PERSISTED", "FINISHED", "CLEAR", 1, 1, 1, false, "ZERO", "managed-only", "NONE"
   ),
   TERM_BEFORE_CLAIM(
      CreationExecutionBoundary.STARTUP_RECOVERY_GATE_CLOSED,
      "DISPATCHER_WORKER",
      "143",
      "QUEUED",
      "PENDING",
      "NOT_SENT",
      "ACTIVE",
      "CLEAR",
      0,
      0,
      0,
      true,
      Round9CcScenario.RecoveryProviderCallExpectation.ZERO.value(),
      "none",
      "NONE"
   ),
   TERM_AFTER_CLAIM(
      CreationExecutionBoundary.CLAIM_COMMITTED_BEFORE_SUBMIT,
      "DISPATCHER_WORKER",
      "143",
      "QUEUED",
      "PENDING",
      "NOT_SENT",
      "ACTIVE",
      "CLEAR",
      0,
      0,
      0,
      true,
      Round9CcScenario.RecoveryProviderCallExpectation.ZERO.value(),
      "none",
      "NONE"
   ),
   TERM_DURING_NOT_SENT(
      CreationExecutionBoundary.STEP_RUNNING_BEFORE_SEND_STARTED,
      "DISPATCHER_WORKER",
      "143",
      "QUEUED",
      "PENDING",
      "NOT_SENT",
      "ACTIVE",
      "CLEAR",
      0,
      0,
      0,
      true,
      Round9CcScenario.RecoveryProviderCallExpectation.ZERO.value(),
      "none",
      "NONE"
   ),
   INT_AFTER_SEND_STARTED(
      CreationExecutionBoundary.SEND_STARTED_COMMITTED,
      "DISPATCHER_WORKER",
      "130",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      0,
      0,
      0,
      false,
      Round9CcScenario.RecoveryProviderCallExpectation.ZERO.value(),
      "none",
      "PROVIDER_DISPATCH_AMBIGUOUS"
   ),
   KILL_DURING_MOCK(
      CreationExecutionBoundary.MOCK_DURING_EXECUTION,
      "DISPATCHER_WORKER",
      "137",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      1,
      0,
      0,
      false,
      "ZERO",
      "staging-may-remain",
      "PROVIDER_DISPATCH_AMBIGUOUS"
   ),
   TIMEOUT_DURING_MOCK(
      CreationExecutionBoundary.MOCK_DURING_EXECUTION,
      "DISPATCHER_WORKER",
      "124",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      1,
      0,
      0,
      false,
      "ZERO",
      "staging-may-remain",
      "PROVIDER_DISPATCH_AMBIGUOUS"
   ),
   HALT_AFTER_VALIDATION(
      CreationExecutionBoundary.VALIDATED_BEFORE_MANAGED_PERSISTENCE,
      "DISPATCHER_WORKER",
      "86",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      1,
      0,
      0,
      false,
      "ZERO",
      "staging-may-remain",
      "PROVIDER_DISPATCH_AMBIGUOUS"
   ),
   EXECUTOR_REJECTION(
      CreationExecutionBoundary.CLAIM_COMMITTED_BEFORE_SUBMIT,
      "DISPATCHER_WORKER",
      "0",
      "QUEUED",
      "PENDING",
      "NOT_SENT",
      "ACTIVE",
      "CLEAR",
      0,
      0,
      0,
      true,
      "ZERO",
      "none",
      "NONE"
   ),
   STARTUP_DATABASE_UNAVAILABLE(
      CreationExecutionBoundary.STARTUP_RECOVERY_GATE_CLOSED,
      "RECOVERY",
      "0",
      "RUNNING",
      "PENDING",
      "NOT_SENT",
      "ACTIVE",
      "PRESENT",
      0,
      0,
      0,
      false,
      "ZERO",
      "none",
      "CREATION_RECOVERY_NOT_READY"
   ),
   SQLITE_BUSY_HEARTBEAT(null, "DISPATCHER_WORKER", "0", "RUNNING", "PENDING", "NOT_SENT", "ACTIVE", "PRESENT", 0, 0, 0, false, "ZERO", "none", "NONE"),
   HEARTBEAT_WINS_FENCE_RACE(null, "DISPATCHER_WORKER", "0", "RUNNING", "PENDING", "NOT_SENT", "ACTIVE", "PRESENT", 0, 0, 0, false, "ZERO", "none", "NONE"),
   RECOVERY_WINS_HEARTBEAT_RACE(null, "RECOVERY", "0", "QUEUED", "PENDING", "NOT_SENT", "ACTIVE", "CLEAR", 0, 0, 0, true, "ZERO", "none", "NONE"),
   TWO_RECOVERY_FENCE_RACE(null, "RECOVERY_A_B", "0", "QUEUED", "PENDING", "NOT_SENT", "ACTIVE", "CLEAR", 0, 0, 0, true, "ZERO", "none", "NONE"),
   STALE_WORKER_RESULT_AFTER_FENCE(
      CreationExecutionBoundary.MOCK_RETURNED_BEFORE_VALIDATION,
      "DISPATCHER_WORKER",
      "0",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      1,
      1,
      1,
      false,
      "ZERO",
      "staging-empty",
      "PROVIDER_DISPATCH_AMBIGUOUS"
   ),
   SUCCESSFUL_PREFIX_RESTART(
      CreationExecutionBoundary.BETWEEN_SUCCEEDED_STEPS,
      "DISPATCHER_WORKER",
      "143",
      "QUEUED",
      "PENDING",
      "NOT_SENT",
      "ACTIVE",
      "CLEAR",
      1,
      1,
      1,
      true,
      "ZERO",
      "managed-prefix",
      "NONE"
   ),
   ALL_SUCCEEDED_FINALIZATION(
      null,
      "RECOVERY",
      "0",
      "SUCCEEDED",
      "SUCCEEDED",
      "RESULT_PERSISTED",
      "FINISHED",
      "CLEAR",
      0,
      0,
      0,
      false,
      "ZERO",
      "managed-only",
      "RECOVERY_FINALIZED_FROM_PERSISTED_RESULT"
   ),
   MISSING_MANAGED_FILE(
      null,
      "RECOVERY",
      "0",
      "FAILED",
      "SUCCEEDED",
      "RESULT_PERSISTED",
      "FINISHED",
      "CLEAR",
      0,
      0,
      0,
      false,
      "ZERO",
      "missing-known-managed-file",
      "CREATION_RESULT_PERSISTENCE_INCONSISTENT"
   ),
   MANAGED_STORAGE_PERMISSION_DENIED(
      CreationExecutionBoundary.MANAGED_FILE_BEFORE_DB_COMMIT,
      "DISPATCHER_WORKER",
      "0",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      1,
      1,
      1,
      false,
      "ZERO",
      "none",
      "CREATION_PERSISTENCE_FAILED"
   ),
   STAGING_SYMLINK_SUBSTITUTION(
      CreationExecutionBoundary.MOCK_DURING_EXECUTION,
      "DISPATCHER_WORKER",
      "0",
      "FAILED",
      "FAILED",
      "SEND_STARTED",
      "FINISHED",
      "CLEAR",
      1,
      0,
      0,
      false,
      "ZERO",
      "none",
      "CREATION_PROVIDER_OUTPUT_INVALID"
   ),
   ARTIFACT_CLOSE_FAILURE(
      CreationExecutionBoundary.RESULT_COMMITTED_BEFORE_ARTIFACT_CLOSE,
      "DISPATCHER_WORKER",
      "0",
      "SUCCEEDED",
      "SUCCEEDED",
      "RESULT_PERSISTED",
      "FINISHED",
      "CLEAR",
      1,
      1,
      1,
      false,
      "ZERO",
      "contained-staging-residue",
      "NONE"
   ),
   CLEANUP_FAILURE(null, "SUPERVISOR", "0", "N/A", "N/A", "N/A", "N/A", "N/A", 0, 0, 0, false, "ZERO", "private-diagnostic-retained", "CLEANUP_FAILED");

   private final Round9CcScenario.Definition definition;

   Round9CcScenario(
      CreationExecutionBoundary failpoint,
      String role,
      String expectedExit,
      String creationStatus,
      String stepStatus,
      String dispatchState,
      String attemptState,
      String claimLease,
      int entry,
      int returned,
      int close,
      boolean ordinaryDispatch,
      String recoveryCalls,
      String expectedFiles,
      String safeCode
   ) {
      this.definition = new Round9CcScenario.Definition(
         failpoint,
         role,
         expectedExit,
         creationStatus,
         stepStatus,
         dispatchState,
         attemptState,
         claimLease,
         entry,
         returned,
         close,
         ordinaryDispatch,
         recoveryCalls,
         expectedFiles,
         safeCode
      );
   }

   public Round9CcScenario.Definition definition() {
      return this.definition;
   }

   boolean isBatch1() {
      return this == TERM_BEFORE_CLAIM || this == TERM_AFTER_CLAIM || this == TERM_DURING_NOT_SENT || this == INT_AFTER_SEND_STARTED;
   }

   boolean supports(Round9CcRunPhase phase) {
      return switch (phase) {
         case INITIAL -> true;
         case SEED, RECOVERY -> this.isBatch1();
      };
   }

   CreationExecutionBoundary failpointFor(Round9CcRunPhase phase) {
      return phase == Round9CcRunPhase.INITIAL ? this.definition.failpoint() : null;
   }

   String roleFor(Round9CcRunPhase phase) {
      return switch (phase) {
         case INITIAL -> this.definition.role();
         case SEED -> "SEEDER";
         case RECOVERY -> "RECOVERY";
      };
   }

   String expectedExitFor(Round9CcRunPhase phase) {
      return phase == Round9CcRunPhase.INITIAL ? this.definition.expectedExit() : "0";
   }

   public static Round9CcScenario require(String value) {
      try {
         return value == null ? null : valueOf(value);
      } catch (IllegalArgumentException exception) {
         throw new IllegalArgumentException("ROUND 9C-C scenario is invalid");
      }
   }

   public Map<String, String> manifestValues() {
      Map<String, String> values = new LinkedHashMap<>();
      values.put("scenario", this.name());
      values.put("failpoint", this.definition.failpoint() == null ? "NONE" : this.definition.failpoint().name());
      values.put("processRole", this.definition.role());
      values.put("expectedExit", this.definition.expectedExit());
      values.put("initialFailpoint", value(this.failpointFor(Round9CcRunPhase.INITIAL)));
      values.put("seedFailpoint", value(this.failpointFor(Round9CcRunPhase.SEED)));
      values.put("recoveryFailpoint", value(this.failpointFor(Round9CcRunPhase.RECOVERY)));
      values.put("initialRole", this.roleFor(Round9CcRunPhase.INITIAL));
      values.put("seedRole", this.roleFor(Round9CcRunPhase.SEED));
      values.put("recoveryRole", this.roleFor(Round9CcRunPhase.RECOVERY));
      values.put("initialExpectedExit", this.expectedExitFor(Round9CcRunPhase.INITIAL));
      values.put("seedExpectedExit", this.expectedExitFor(Round9CcRunPhase.SEED));
      values.put("recoveryExpectedExit", this.expectedExitFor(Round9CcRunPhase.RECOVERY));
      values.put("requiresRecoveryRestart", String.valueOf(this.isBatch1()));
      values.put("expectedCreationStatus", this.definition.creationStatus());
      values.put("expectedStepStatus", this.definition.stepStatus());
      values.put("expectedDispatchState", this.definition.dispatchState());
      values.put("expectedAttemptState", this.definition.attemptState());
      values.put("expectedClaimLease", this.definition.claimLease());
      values.put("expectedMockEntry", String.valueOf(this.definition.entry()));
      values.put("expectedMockReturn", String.valueOf(this.definition.returned()));
      values.put("expectedMockClose", String.valueOf(this.definition.close()));
      values.put("ordinaryDispatchResumes", String.valueOf(this.definition.ordinaryDispatch()));
      values.put("recoveryProviderCalls", this.definition.recoveryCalls());
      values.put("expectedFiles", this.definition.expectedFiles());
      values.put("safeCode", this.definition.safeCode());
      return Map.copyOf(values);
   }

   private static String value(CreationExecutionBoundary boundary) {
      return boundary == null ? "NONE" : boundary.name();
   }

   public record Definition(
      CreationExecutionBoundary failpoint,
      String role,
      String expectedExit,
      String creationStatus,
      String stepStatus,
      String dispatchState,
      String attemptState,
      String claimLease,
      int entry,
      int returned,
      int close,
      boolean ordinaryDispatch,
      String recoveryCalls,
      String expectedFiles,
      String safeCode
   ) {
   }

   private enum RecoveryProviderCallExpectation {
      ZERO;

      String value() {
         return this.name();
      }
   }
}

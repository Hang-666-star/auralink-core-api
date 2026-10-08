package com.auralink.ops.round9cc;

import com.auralink.api.v1.creation.CreationQueuedResponse;
import com.auralink.api.v1.creation.CreationSourceRequest;
import com.auralink.api.v1.creation.CreationSubmissionRequest;
import com.auralink.creation.CreationQueueDispatcher;
import com.auralink.creation.CreationRecoveryGate;
import com.auralink.creation.CreationStatus;
import com.auralink.creation.CreationStepStatus;
import com.auralink.creation.CreationSubmissionService;
import com.auralink.entity.Creation;
import com.auralink.entity.CreationStep;
import com.auralink.entity.User;
import com.auralink.entity.UserWorkflow;
import com.auralink.repository.CreationExecutionAttemptRepository;
import com.auralink.repository.CreationRepository;
import com.auralink.repository.CreationStepRepository;
import com.auralink.repository.GenerationLogRepository;
import com.auralink.repository.PaintingRepository;
import com.auralink.repository.UserRepository;
import com.auralink.repository.UserWorkflowRepository;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.graph.CanonicalWorkflowEdge;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.auralink.workflow.graph.CanonicalWorkflowNode;
import com.auralink.workflow.graph.WorkflowGraphCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

final class Round9CcBatch1SeedCoordinator {
   private final Round9CcPackagedFailureHarness.HarnessState state;
   private final UserRepository users;
   private final UserWorkflowRepository workflows;
   private final CreationSubmissionService submissions;
   private final CreationQueueDispatcher dispatcher;
   private final CreationRecoveryGate recoveryGate;
   private final CreationRepository creations;
   private final CreationStepRepository steps;
   private final CreationExecutionAttemptRepository executionAttempts;
   private final GenerationLogRepository generationLogs;
   private final PaintingRepository paintings;
   private final WorkflowGraphCodec workflowCodec;

   Round9CcBatch1SeedCoordinator(
      Round9CcPackagedFailureHarness.HarnessState state,
      UserRepository users,
      UserWorkflowRepository workflows,
      CreationSubmissionService submissions,
      CreationQueueDispatcher dispatcher,
      CreationRecoveryGate recoveryGate,
      CreationRepository creations,
      CreationStepRepository steps,
      CreationExecutionAttemptRepository executionAttempts,
      GenerationLogRepository generationLogs,
      PaintingRepository paintings,
      WorkflowGraphCodec workflowCodec
   ) {
      this.state = state;
      this.users = users;
      this.workflows = workflows;
      this.submissions = submissions;
      this.dispatcher = dispatcher;
      this.recoveryGate = recoveryGate;
      this.creations = creations;
      this.steps = steps;
      this.executionAttempts = executionAttempts;
      this.generationLogs = generationLogs;
      this.paintings = paintings;
      this.workflowCodec = workflowCodec;
   }

   void seed(Round9CcPackagedFailureHarness.Launch launch) {
      require(launch, Round9CcRunPhase.SEED);
      if (this.recoveryGate.isOpen() && this.creations.count() == 0L && this.generationLogs.count() == 0L && this.paintings.count() == 0L) {
         User owner = (User)this.users
            .saveAndFlush(
               User.builder()
                  .username("round9cc-batch1-" + launch.instance())
                  .password("fixture-only")
                  .fullName("ROUND 9C-C Batch 1 Fixture")
                  .email("round9cc-batch1-" + launch.instance() + "@example.invalid")
                  .build()
            );
         UserWorkflow workflow = (UserWorkflow)this.workflows
            .saveAndFlush(
               UserWorkflow.builder()
                  .user(owner)
                  .name("ROUND 9C-C " + launch.scenario().name())
                  .graphJson(
                     this.workflowCodec
                        .encode(
                           new CanonicalWorkflowGraph(
                              1,
                              List.of(
                                 CanonicalWorkflowNode.source("source", WorkflowModality.TEXT_DESCRIPTION),
                                 CanonicalWorkflowNode.transform(
                                    "painting", WorkflowOperation.TEXT_TO_PAINTING, "seedream-5", WorkflowModality.TEXT_DESCRIPTION, WorkflowModality.PAINTING
                                 )
                              ),
                              List.of(new CanonicalWorkflowEdge("source", "painting"))
                           )
                        )
                  )
                  .schemaVersion(1)
                  .status("ACTIVE")
                  .build()
            );
         CreationQueuedResponse queued = this.submit(owner, workflow, launch.scenario());
         Creation creation = this.creations.findByPublicId(queued.creationId()).orElseThrow(Round9CcBatch1SeedCoordinator::invalid);
         this.assertQueuedSeed(creation);
         Round9CcPackagedFailureHarness.writePrivate(
            launch.fixture().runtimeFile(launch.instance(), "seed"),
            "SCENARIO=" + launch.scenario().name() + "\nROLE=" + launch.role() + "\nCREATIONS=1\nEXECUTION_ATTEMPTS=1\nMOCK_PROVIDER_CALLS=0\n"
         );
      } else {
         throw invalid();
      }
   }

   void requireSeeded(Round9CcPackagedFailureHarness.Launch launch) {
      require(launch, Round9CcRunPhase.INITIAL);
      Creation creation = this.onlyCreation();
      if (this.generationLogs.count() == 0L && this.paintings.count() == 0L) {
         this.assertQueuedSeed(creation);
      } else {
         throw invalid();
      }
   }

   void beginInitialExecution(Round9CcPackagedFailureHarness.Launch launch) {
      require(launch, Round9CcRunPhase.INITIAL);
      if (launch.scenario() != Round9CcScenario.TERM_BEFORE_CLAIM) {
         if (!this.recoveryGate.isOpen()) {
            throw invalid();
         }

         this.dispatcher.dispatchOne();
      }
   }

   void verifyRecovered(Round9CcPackagedFailureHarness.Launch launch) {
      require(launch, Round9CcRunPhase.RECOVERY);
      if (this.recoveryGate.isOpen() && this.generationLogs.count() == 0L && this.paintings.count() == 0L) {
         Creation creation = this.onlyCreation();
         List<CreationStep> persistedSteps = this.steps.findByCreationIdOrderByStepIndexAsc(creation.getId());
         Round9CcScenario.Definition expected = launch.scenario().definition();
         if (persistedSteps.size() == 1
            && expected.creationStatus().equals(creation.getStatus())
            && expected.stepStatus().equals(persistedSteps.get(0).getStatus())
            && expected.dispatchState().equals(persistedSteps.get(0).getProviderDispatchState())
            && this.executionAttempts.countByCreationId(creation.getId()) == 1L
            && this.activeAttemptMatches(creation, expected.attemptState())
            && this.claimLeaseMatches(creation, expected.claimLease())
            && this.safeCodeMatches(creation, expected.safeCode())
            && this.journalIsExpected(expected)
            && this.fixtureFilesMatch(launch.fixture(), expected.expectedFiles())) {
            Round9CcPackagedFailureHarness.writePrivate(
               launch.fixture().runtimeFile(launch.instance(), "recovery"),
               "SCENARIO="
                  + launch.scenario().name()
                  + "\nROLE="
                  + launch.role()
                  + "\nRECOVERY_GATE_OPEN\nRECOVERY_PROVIDER_CALLS="
                  + expected.recoveryCalls()
                  + "\nORDINARY_DISPATCH_RESUMES="
                  + expected.ordinaryDispatch()
                  + "\n"
            );
         } else {
            throw invalid();
         }
      } else {
         throw invalid();
      }
   }

   private CreationQueuedResponse submit(User owner, UserWorkflow workflow, Round9CcScenario scenario) {
      CreationSourceRequest source = new CreationSourceRequest();
      source.setModality(WorkflowModality.TEXT_DESCRIPTION.name());
      source.setText("ROUND9CC_BATCH1_" + scenario.name());
      CreationSubmissionRequest request = new CreationSubmissionRequest();
      request.setWorkflowId(workflow.getPublicId());
      request.setSource(source);
      SecurityContextHolder.getContext()
         .setAuthentication(new UsernamePasswordAuthenticationToken(owner.getUsername(), "fixture-only", owner.getAuthorities()));

      try {
         return this.submissions.submit(request);
      } finally {
         SecurityContextHolder.clearContext();
      }
   }

   private void assertQueuedSeed(Creation creation) {
      List<CreationStep> persistedSteps = this.steps.findByCreationIdOrderByStepIndexAsc(creation.getId());
      if (!CreationStatus.QUEUED.name().equals(creation.getStatus())
         || creation.getClaimToken() != null
         || creation.getLeaseExpiresAt() != null
         || persistedSteps.size() != 1
         || !CreationStepStatus.PENDING.name().equals(persistedSteps.get(0).getStatus())
         || !"NOT_SENT".equals(persistedSteps.get(0).getProviderDispatchState())
         || this.executionAttempts.countByCreationId(creation.getId()) != 1L
         || this.executionAttempts.findByCreationIdAndFinishedAtIsNull(creation.getId()).isEmpty()) {
         throw invalid();
      }
   }

   private Creation onlyCreation() {
      if (this.creations.count() != 1L) {
         throw invalid();
      } else {
         return (Creation)this.creations.findAll().stream().findFirst().orElseThrow(Round9CcBatch1SeedCoordinator::invalid);
      }
   }

   private boolean activeAttemptMatches(Creation creation, String expected) {
      boolean active = this.executionAttempts.findByCreationIdAndFinishedAtIsNull(creation.getId()).isPresent();
      return "ACTIVE".equals(expected) && active || "FINISHED".equals(expected) && !active;
   }

   private boolean claimLeaseMatches(Creation creation, String expected) {
      boolean clear = creation.getClaimToken() == null && creation.getLeaseExpiresAt() == null;
      boolean present = creation.getClaimToken() != null && creation.getLeaseExpiresAt() != null;
      return "CLEAR".equals(expected) && clear || "PRESENT".equals(expected) && present;
   }

   private boolean safeCodeMatches(Creation creation, String expected) {
      return "NONE".equals(expected)
         ? creation.getErrorCode() == null && creation.getErrorMessage() == null
         : expected.equals(creation.getErrorCode()) && expected.equals(creation.getErrorMessage());
   }

   private boolean journalIsExpected(Round9CcScenario.Definition expected) {
      List<Round9CcMockJournal.Record> records = Round9CcMockJournal.read(this.state.journal().file());
      return count(records, Round9CcMockJournal.Event.ENTRY) == expected.entry()
         && count(records, Round9CcMockJournal.Event.RETURN) == expected.returned()
         && count(records, Round9CcMockJournal.Event.CLOSE) == expected.close();
   }

   private boolean fixtureFilesMatch(Round9CcFixture fixture, String expected) {
      return !"none".equals(expected)
         ? false
         : this.directoryEmpty(fixture.requireDirectory("managed")) && this.directoryEmpty(fixture.requireDirectory("provider-staging"));
   }

   private boolean directoryEmpty(Path directory) {
      try (Stream<Path> entries = Files.list(directory)) {
         return entries.findAny().isEmpty();
      } catch (IOException exception) {
         return false;
      }
   }

   private static int count(List<Round9CcMockJournal.Record> records, Round9CcMockJournal.Event event) {
      return (int)records.stream().filter(record -> record.event() == event).count();
   }

   private static void require(Round9CcPackagedFailureHarness.Launch launch, Round9CcRunPhase phase) {
      if (launch == null || !launch.scenario().isBatch1() || launch.phase() != phase) {
         throw invalid();
      }
   }

   private static IllegalStateException invalid() {
      return new IllegalStateException("ROUND9CC_ERROR:BATCH1_STATE_INVALID");
   }
}

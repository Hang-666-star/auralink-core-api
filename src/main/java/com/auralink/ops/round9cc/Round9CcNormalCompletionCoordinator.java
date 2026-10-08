package com.auralink.ops.round9cc;

import com.auralink.api.v1.creation.CreationQueuedResponse;
import com.auralink.api.v1.creation.CreationSourceRequest;
import com.auralink.api.v1.creation.CreationSubmissionRequest;
import com.auralink.creation.CreationQueueDispatcher;
import com.auralink.creation.CreationRecoveryGate;
import com.auralink.creation.CreationStatus;
import com.auralink.creation.CreationStepStatus;
import com.auralink.creation.CreationSubmissionService;
import com.auralink.creation.ProviderDispatchState;
import com.auralink.entity.Creation;
import com.auralink.entity.CreationStep;
import com.auralink.entity.CreationStepDispatchAttempt;
import com.auralink.entity.User;
import com.auralink.entity.UserWorkflow;
import com.auralink.repository.CreationExecutionAttemptRepository;
import com.auralink.repository.CreationRepository;
import com.auralink.repository.CreationStepDispatchAttemptRepository;
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
import java.time.Duration;
import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

final class Round9CcNormalCompletionCoordinator {
   private static final Duration MAX_TIMEOUT = Duration.ofSeconds(60L);
   private final Round9CcPackagedFailureHarness.HarnessState state;
   private final UserRepository users;
   private final UserWorkflowRepository workflows;
   private final CreationSubmissionService submissions;
   private final CreationQueueDispatcher dispatcher;
   private final CreationRecoveryGate recoveryGate;
   private final CreationRepository creations;
   private final CreationStepRepository steps;
   private final CreationExecutionAttemptRepository executionAttempts;
   private final CreationStepDispatchAttemptRepository dispatchAttempts;
   private final GenerationLogRepository generationLogs;
   private final PaintingRepository paintings;
   private final WorkflowGraphCodec workflowCodec;

   Round9CcNormalCompletionCoordinator(ConfigurableApplicationContext context, Round9CcPackagedFailureHarness.HarnessState state) {
      this.state = state;
      this.users = (UserRepository)context.getBean(UserRepository.class);
      this.workflows = (UserWorkflowRepository)context.getBean(UserWorkflowRepository.class);
      this.submissions = (CreationSubmissionService)context.getBean(CreationSubmissionService.class);
      this.dispatcher = (CreationQueueDispatcher)context.getBean(CreationQueueDispatcher.class);
      this.recoveryGate = (CreationRecoveryGate)context.getBean(CreationRecoveryGate.class);
      this.creations = (CreationRepository)context.getBean(CreationRepository.class);
      this.steps = (CreationStepRepository)context.getBean(CreationStepRepository.class);
      this.executionAttempts = (CreationExecutionAttemptRepository)context.getBean(CreationExecutionAttemptRepository.class);
      this.dispatchAttempts = (CreationStepDispatchAttemptRepository)context.getBean(CreationStepDispatchAttemptRepository.class);
      this.generationLogs = (GenerationLogRepository)context.getBean(GenerationLogRepository.class);
      this.paintings = (PaintingRepository)context.getBean(PaintingRepository.class);
      this.workflowCodec = (WorkflowGraphCodec)context.getBean(WorkflowGraphCodec.class);
   }

   Round9CcNormalCompletionCoordinator.Completion run(Round9CcPackagedFailureHarness.Launch launch) {
      if (launch.scenario() == Round9CcScenario.NORMAL_COMPLETION && this.recoveryGate.isOpen()) {
         Duration timeout = boundedTimeout(launch.timeout());
         if (this.creations.count() == 0L && this.generationLogs.count() == 0L && this.paintings.count() == 0L) {
            User owner = (User)this.users
               .saveAndFlush(
                  User.builder()
                     .username("round9cc-" + launch.instance())
                     .password("fixture-only")
                     .fullName("ROUND 9C-C Fixture")
                     .email("round9cc-" + launch.instance() + "@example.invalid")
                     .build()
               );
            UserWorkflow workflow = (UserWorkflow)this.workflows
               .saveAndFlush(
                  UserWorkflow.builder()
                     .user(owner)
                     .name("ROUND 9C-C NORMAL_COMPLETION")
                     .graphJson(
                        this.workflowCodec
                           .encode(
                              new CanonicalWorkflowGraph(
                                 1,
                                 List.of(
                                    CanonicalWorkflowNode.source("source", WorkflowModality.TEXT_DESCRIPTION),
                                    CanonicalWorkflowNode.transform(
                                       "painting",
                                       WorkflowOperation.TEXT_TO_PAINTING,
                                       "seedream-5",
                                       WorkflowModality.TEXT_DESCRIPTION,
                                       WorkflowModality.PAINTING
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
            CreationQueuedResponse queued = this.submit(owner, workflow);
            Creation creation = this.creations
               .findByPublicId(queued.creationId())
               .orElseThrow(() -> new IllegalStateException("ROUND 9C-C normal completion seed is missing"));
            if (this.creations.count() == 1L
               && CreationStatus.QUEUED.name().equals(creation.getStatus())
               && this.executionAttempts.countByCreationId(creation.getId()) == 1L
               && this.steps.findByCreationIdOrderByStepIndexAsc(creation.getId()).size() == 1) {
               this.dispatcher.dispatchOne();
               return this.verifyTerminal(this.awaitTerminal(queued.creationId(), launch.scenario(), timeout), launch.scenario());
            } else {
               throw new IllegalStateException("ROUND 9C-C normal completion seed is invalid");
            }
         } else {
            throw new IllegalStateException("ROUND 9C-C normal completion fixture is not empty");
         }
      } else {
         throw new IllegalStateException("ROUND 9C-C normal completion cannot start safely");
      }
   }

   private CreationQueuedResponse submit(User owner, UserWorkflow workflow) {
      CreationSourceRequest source = new CreationSourceRequest();
      source.setModality(WorkflowModality.TEXT_DESCRIPTION.name());
      source.setText("ROUND9CC_NORMAL_COMPLETION");
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

   private Creation awaitTerminal(String publicId, Round9CcScenario scenario, Duration timeout) {
      long deadline = System.nanoTime() + timeout.toNanos();

      while (System.nanoTime() < deadline) {
         Creation creation = this.creations
            .findByPublicId(publicId)
            .orElseThrow(() -> new IllegalStateException("ROUND 9C-C normal completion seed is missing"));
         if (CreationStatus.SUCCEEDED.name().equals(creation.getStatus()) && this.matchesExpectedJournal(scenario)) {
            return creation;
         }

         try {
            Thread.sleep(25L);
         } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ROUND 9C-C normal completion interrupted");
         }
      }

      throw new IllegalStateException("ROUND 9C-C normal completion timed out");
   }

   private Round9CcNormalCompletionCoordinator.Completion verifyTerminal(Creation creation, Round9CcScenario scenario) {
      List<CreationStep> persistedSteps = this.steps.findByCreationIdOrderByStepIndexAsc(creation.getId());
      if (CreationStatus.SUCCEEDED.name().equals(creation.getStatus())
         && creation.getClaimToken() == null
         && creation.getLeaseExpiresAt() == null
         && creation.getRetryVersion() == 0
         && persistedSteps.size() == 1
         && this.executionAttempts.countByCreationId(creation.getId()) == 1L
         && this.generationLogs.count() == 0L
         && this.paintings.count() == 0L) {
         CreationStep step = persistedSteps.get(0);
         List<CreationStepDispatchAttempt> dispatches = this.dispatchAttempts.findByCreationStepIdOrderByIdAsc(step.getId());
         if (CreationStepStatus.SUCCEEDED.name().equals(step.getStatus())
            && ProviderDispatchState.RESULT_PERSISTED.name().equals(step.getProviderDispatchState())
            && step.getAttemptCount() == 1
            && dispatches.size() == 1
            && ProviderDispatchState.RESULT_PERSISTED.name().equals(dispatches.get(0).getDispatchState())
            && !this.executionAttempts.findByCreationIdAndFinishedAtIsNull(creation.getId()).isPresent()
            && this.matchesExpectedJournal(scenario)) {
            List<Round9CcMockJournal.Record> journal = Round9CcMockJournal.read(this.state.journal().file());
            return new Round9CcNormalCompletionCoordinator.Completion(
               creation.getPublicId(),
               creation.getStatus(),
               step.getStatus(),
               step.getProviderDispatchState(),
               this.executionAttempts.findByCreationIdAndFinishedAtIsNull(creation.getId()).isEmpty(),
               creation.getClaimToken() == null && creation.getLeaseExpiresAt() == null,
               creation.getRetryVersion(),
               count(journal, Round9CcMockJournal.Event.ENTRY),
               count(journal, Round9CcMockJournal.Event.RETURN),
               count(journal, Round9CcMockJournal.Event.CLOSE)
            );
         } else {
            throw new IllegalStateException("ROUND 9C-C normal completion evidence is invalid");
         }
      } else {
         throw new IllegalStateException("ROUND 9C-C normal completion terminal state is invalid");
      }
   }

   private boolean matchesExpectedJournal(Round9CcScenario scenario) {
      List<Round9CcMockJournal.Record> journal = Round9CcMockJournal.read(this.state.journal().file());
      Round9CcScenario.Definition expected = scenario.definition();
      return count(journal, Round9CcMockJournal.Event.ENTRY) == expected.entry()
         && count(journal, Round9CcMockJournal.Event.RETURN) == expected.returned()
         && count(journal, Round9CcMockJournal.Event.CLOSE) == expected.close();
   }

   private static int count(List<Round9CcMockJournal.Record> journal, Round9CcMockJournal.Event event) {
      return (int)journal.stream().filter(record -> record.event() == event).count();
   }

   static Duration boundedTimeout(Duration timeout) {
      if (timeout != null && !timeout.isZero() && !timeout.isNegative() && timeout.compareTo(MAX_TIMEOUT) <= 0) {
         return timeout;
      } else {
         throw new IllegalArgumentException("ROUND 9C-C normal completion timeout is invalid");
      }
   }

   record Completion(
      String creationId,
      String creationStatus,
      String stepStatus,
      String dispatchState,
      boolean executionFinished,
      boolean claimAndLeaseClear,
      int retryVersion,
      int entries,
      int returns,
      int closes
   ) {
   }
}

package com.auralink.workflow.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.entity.User;
import com.auralink.entity.UserWorkflow;
import com.auralink.repository.UserWorkflowRepository;
import com.auralink.workflow.WorkflowLifecycleStatus;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowNodeKind;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.auralink.workflow.graph.CanonicalWorkflowNode;
import com.auralink.workflow.graph.WorkflowGraphCodec;
import com.auralink.workflow.graph.WorkflowValidationResult;
import com.auralink.workflow.graph.WorkflowValidator;
import com.auralink.workflow.graph.WorkflowViolationCode;
import com.auralink.workflow.snapshot.WorkflowSnapshot;
import com.auralink.workflow.snapshot.WorkflowSnapshotFactory;
import com.auralink.workflow.snapshot.WorkflowSnapshotResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorkflowExecutionPreparer {
   private final UserWorkflowRepository workflows;
   private final WorkflowGraphCodec graphCodec;
   private final WorkflowValidator validator;
   private final WorkflowSnapshotFactory snapshotFactory;

   public WorkflowExecutionPreparer.PreparedWorkflow prepare(String workflowId, User owner) {
      String canonicalWorkflowId = this.canonicalWorkflowId(workflowId);
      UserWorkflow workflow = this.workflows
         .findByPublicIdAndUser_Id(canonicalWorkflowId, owner.getId())
         .orElseThrow(WorkflowExecutionPreparer::workflowNotFound);
      if (!WorkflowLifecycleStatus.ACTIVE.name().equals(workflow.getStatus())) {
         throw workflowDraft();
      }

      CanonicalWorkflowGraph storedGraph;
      try {
         storedGraph = this.graphCodec.decode(workflow.getGraphJson());
      } catch (RuntimeException exception) {
         throw workflowInvalid();
      }

      WorkflowValidationResult validation = this.validator.validate(WorkflowStoredRequestFactory.from(workflow, storedGraph));
      if (validation.valid() && validation.canonicalization() != null && workflow.getGraphJson().equals(validation.canonicalization().canonicalJson())) {
         WorkflowSnapshotResult snapshotResult = this.snapshotFactory
            .create(workflow.getPublicId(), workflow.getName(), workflow.getSchemaVersion(), validation.canonicalization().graph());
         List<WorkflowExecutionPreparer.PreparedTransform> transforms = validation.canonicalization()
            .graph()
            .nodes()
            .stream()
            .filter(node -> node.kind() == WorkflowNodeKind.TRANSFORM)
            .map(this::toTransform)
            .toList();
         if (transforms.isEmpty()) {
            throw workflowInvalid();
         } else {
            return new WorkflowExecutionPreparer.PreparedWorkflow(
               workflow,
               snapshotResult.snapshot(),
               snapshotResult.canonicalJson(),
               validation.canonicalization().sourceModality(),
               validation.canonicalization().terminalModality(),
               transforms
            );
         }
      } else if (storedGraph.schemaVersion() != 1
         && !validation.violations().stream().anyMatch(violation -> WorkflowViolationCode.REPEATED_PRODUCT_NODE_TYPE.name().equals(violation.code()))) {
         throw workflowInvalid();
      } else {
         throw workflowConversionRequired();
      }
   }

   private WorkflowExecutionPreparer.PreparedTransform toTransform(CanonicalWorkflowNode node) {
      if (node.operation() != null && node.providerCode() != null && node.inputModality() != null && node.outputModality() != null) {
         String parametersJson;
         try {
            parametersJson = this.graphCodec.canonicalMapperCopy().writeValueAsString(node.parameters());
         } catch (Exception exception) {
            throw workflowInvalid();
         }

         return new WorkflowExecutionPreparer.PreparedTransform(
            node.id(), node.operation(), node.providerCode(), node.inputModality(), node.outputModality(), parametersJson
         );
      } else {
         throw workflowInvalid();
      }
   }

   private String canonicalWorkflowId(String workflowId) {
      try {
         String canonical = UUID.fromString(workflowId).toString();
         if (!canonical.equals(workflowId)) {
            throw workflowNotFound();
         } else {
            return canonical;
         }
      } catch (IllegalArgumentException | NullPointerException exception) {
         throw workflowNotFound();
      }
   }

   private static ApiV1Exception workflowNotFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.WORKFLOW_NOT_FOUND, "工作流不存在");
   }

   private static ApiV1Exception workflowInvalid() {
      return new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.WORKFLOW_INVALID, "工作流定义验证失败");
   }

   private static ApiV1Exception workflowDraft() {
      return new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.WORKFLOW_DRAFT_NOT_EXECUTABLE, "工作流草稿不能提交创作");
   }

   private static ApiV1Exception workflowConversionRequired() {
      return new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.WORKFLOW_CONVERSION_REQUIRED, "历史工作流不符合当前顺序规则，请编辑并保存后再提交");
   }

   public WorkflowExecutionPreparer(
      final UserWorkflowRepository workflows,
      final WorkflowGraphCodec graphCodec,
      final WorkflowValidator validator,
      final WorkflowSnapshotFactory snapshotFactory
   ) {
      this.workflows = workflows;
      this.graphCodec = graphCodec;
      this.validator = validator;
      this.snapshotFactory = snapshotFactory;
   }

   public record PreparedTransform(
      String nodeId, WorkflowOperation operation, String providerCode, WorkflowModality inputModality, WorkflowModality outputModality, String parametersJson
   ) {
   }

   public record PreparedWorkflow(
      UserWorkflow workflow,
      WorkflowSnapshot snapshot,
      String snapshotJson,
      WorkflowModality sourceModality,
      WorkflowModality terminalModality,
      List<WorkflowExecutionPreparer.PreparedTransform> transforms
   ) {
      public PreparedWorkflow {
         transforms = List.copyOf(transforms);
      }
   }
}

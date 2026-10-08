package com.auralink.workflow.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.api.v1.workflow.WorkflowDefinitionRequest;
import com.auralink.api.v1.workflow.WorkflowDetailResponse;
import com.auralink.api.v1.workflow.WorkflowPageResponse;
import com.auralink.api.v1.workflow.WorkflowSummaryResponse;
import com.auralink.api.v1.workflow.WorkflowValidationResponse;
import com.auralink.config.properties.WorkflowProperties;
import com.auralink.entity.User;
import com.auralink.entity.UserWorkflow;
import com.auralink.repository.UserWorkflowRepository;
import com.auralink.service.CurrentUserService;
import com.auralink.workflow.WorkflowLifecycleStatus;
import com.auralink.workflow.graph.WorkflowValidationResult;
import com.auralink.workflow.graph.WorkflowValidator;
import com.auralink.workflow.graph.WorkflowViolationCode;
import com.auralink.workflow.snapshot.WorkflowSnapshotFactory;
import com.auralink.workflow.snapshot.WorkflowSnapshotResult;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWorkflowService {
   public static final String ACTIVE_STATUS = "ACTIVE";
   public static final String DRAFT_STATUS = "DRAFT";
   private static final int MAX_PAGE_SIZE = 100;
   private final WorkflowFeatureGuard featureGuard;
   private final WorkflowProperties properties;
   private final CurrentUserService currentUserService;
   private final UserWorkflowRepository workflows;
   private final WorkflowValidator validator;
   private final WorkflowResponseMapper responseMapper;
   private final WorkflowSnapshotFactory snapshotFactory;
   private final EntityManager entityManager;

   @Transactional
   public WorkflowDetailResponse create(WorkflowDefinitionRequest request) {
      this.featureGuard.requireEnabled();
      User owner = this.currentUserService.requireCurrentUser();
      WorkflowLifecycleStatus status = this.requestedStatus(request, WorkflowLifecycleStatus.ACTIVE);
      WorkflowValidationResult validation = this.requireValid(request, status);
      UserWorkflow entity = UserWorkflow.builder()
         .user(owner)
         .name(validation.normalizedName())
         .description(validation.normalizedDescription())
         .graphJson(validation.canonicalization().canonicalJson())
         .schemaVersion(validation.canonicalization().graph().schemaVersion())
         .status(status.name())
         .build();
      this.workflows.saveAndFlush(entity);
      this.refreshPersisted(entity);
      return this.responseMapper.detail(entity);
   }

   @Transactional(readOnly = true)
   public WorkflowPageResponse list(int page, int size) {
      this.featureGuard.requireEnabled();
      this.validatePage(page, size);
      User owner = this.currentUserService.requireCurrentUser();
      Sort sort = Sort.by(Direction.DESC, new String[]{"updatedAt"}).and(Sort.by(Direction.ASC, new String[]{"publicId"}));
      Page<UserWorkflow> result = this.workflows.findAllByUser_Id(owner.getId(), PageRequest.of(page, size, sort));
      List<WorkflowSummaryResponse> items = result.getContent().stream().map(this.responseMapper::summary).toList();
      return WorkflowPageResponse.from(result, items);
   }

   @Transactional(readOnly = true)
   public WorkflowDetailResponse get(String workflowId) {
      this.featureGuard.requireEnabled();
      User owner = this.currentUserService.requireCurrentUser();
      return this.responseMapper.detail(this.findOwned(workflowId, owner));
   }

   @Transactional
   public WorkflowDetailResponse replace(String workflowId, WorkflowDefinitionRequest request) {
      this.featureGuard.requireEnabled();
      User owner = this.currentUserService.requireCurrentUser();
      UserWorkflow entity = this.findOwned(workflowId, owner);
      WorkflowLifecycleStatus status = this.requestedStatus(request, this.storedStatus(entity));
      WorkflowValidationResult validation = this.requireValid(request, status);
      entity.setName(validation.normalizedName());
      entity.setDescription(validation.normalizedDescription());
      entity.setGraphJson(validation.canonicalization().canonicalJson());
      entity.setSchemaVersion(validation.canonicalization().graph().schemaVersion());
      entity.setStatus(status.name());
      this.workflows.saveAndFlush(entity);
      this.refreshPersisted(entity);
      return this.responseMapper.detail(entity);
   }

   @Transactional
   public void delete(String workflowId) {
      this.featureGuard.requireEnabled();
      User owner = this.currentUserService.requireCurrentUser();
      UserWorkflow entity = this.findOwned(workflowId, owner);
      this.workflows.delete(entity);
      this.workflows.flush();
   }

   @Transactional(readOnly = true)
   public WorkflowValidationResponse validate(WorkflowDefinitionRequest request) {
      this.featureGuard.requireEnabled();
      this.currentUserService.requireCurrentUser();
      WorkflowLifecycleStatus status = this.requestedStatus(request, WorkflowLifecycleStatus.ACTIVE);
      return WorkflowValidationResponse.from(this.validate(request, status), this.properties.getSchemaVersion());
   }

   @Transactional(readOnly = true)
   public WorkflowSnapshotResult snapshotOwned(String workflowId) {
      this.featureGuard.requireEnabled();
      User owner = this.currentUserService.requireCurrentUser();
      UserWorkflow entity = this.findOwned(workflowId, owner);
      StoredWorkflowDefinition stored = this.responseMapper.parse(entity);
      return this.snapshotFactory.create(entity.getPublicId(), entity.getName(), entity.getSchemaVersion(), stored.graph());
   }

   private WorkflowValidationResult requireValid(WorkflowDefinitionRequest request, WorkflowLifecycleStatus status) {
      WorkflowValidationResult validation = this.validate(request, status);
      if (validation.valid()) {
         return validation;
      }

      ApiErrorCode errorCode = ApiErrorCode.WORKFLOW_INVALID;
      if (this.hasViolation(validation, ApiErrorCode.WORKFLOW_SCHEMA_UNSUPPORTED.name())) {
         errorCode = ApiErrorCode.WORKFLOW_SCHEMA_UNSUPPORTED;
      } else if (this.hasViolation(validation, ApiErrorCode.WORKFLOW_GRAPH_TOO_LARGE.name())) {
         errorCode = ApiErrorCode.WORKFLOW_GRAPH_TOO_LARGE;
      }

      String message = this.hasViolation(validation, WorkflowViolationCode.REPEATED_PRODUCT_NODE_TYPE.name()) ? "同一流程不能重复使用同一类型节点，请删除重复节点后保存" : "工作流定义验证失败";
      throw new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message, validation.violations());
   }

   private boolean hasViolation(WorkflowValidationResult validation, String code) {
      return validation.violations().stream().anyMatch(violation -> code.equals(violation.code()));
   }

   private UserWorkflow findOwned(String workflowId, User owner) {
      String canonicalId = this.canonicalWorkflowId(workflowId);
      return this.workflows.findByPublicIdAndUser_Id(canonicalId, owner.getId()).orElseThrow(UserWorkflowService::notFound);
   }

   private WorkflowValidationResult validate(WorkflowDefinitionRequest request, WorkflowLifecycleStatus status) {
      return status == WorkflowLifecycleStatus.DRAFT ? this.validator.validateDraft(request) : this.validator.validate(request);
   }

   private WorkflowLifecycleStatus requestedStatus(WorkflowDefinitionRequest request, WorkflowLifecycleStatus fallback) {
      return request != null && request.hasStatusField()
         ? WorkflowLifecycleStatus.fromWire(request.getStatus())
            .orElseThrow(() -> new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.WORKFLOW_INVALID, "工作流状态无效"))
         : fallback;
   }

   private WorkflowLifecycleStatus storedStatus(UserWorkflow entity) {
      return WorkflowLifecycleStatus.fromWire(entity.getStatus())
         .orElseThrow(() -> new ApiV1Exception(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.WORKFLOW_INVALID, "已保存工作流状态无效"));
   }

   private String canonicalWorkflowId(String workflowId) {
      try {
         String canonical = UUID.fromString(workflowId).toString();
         if (!canonical.equals(workflowId)) {
            throw notFound();
         } else {
            return canonical;
         }
      } catch (IllegalArgumentException | NullPointerException exception) {
         throw notFound();
      }
   }

   private void validatePage(int page, int size) {
      if (page < 0) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "页码必须大于或等于 0");
      } else if (size < 1 || size > 100) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PAGE_SIZE, "每页数量必须在 1 到 100 之间");
      }
   }

   private void refreshPersisted(UserWorkflow entity) {
      this.entityManager.flush();
      this.entityManager.refresh(entity);
   }

   private static ApiV1Exception notFound() {
      return new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.WORKFLOW_NOT_FOUND, "工作流不存在");
   }

   public UserWorkflowService(
      final WorkflowFeatureGuard featureGuard,
      final WorkflowProperties properties,
      final CurrentUserService currentUserService,
      final UserWorkflowRepository workflows,
      final WorkflowValidator validator,
      final WorkflowResponseMapper responseMapper,
      final WorkflowSnapshotFactory snapshotFactory,
      final EntityManager entityManager
   ) {
      this.featureGuard = featureGuard;
      this.properties = properties;
      this.currentUserService = currentUserService;
      this.workflows = workflows;
      this.validator = validator;
      this.responseMapper = responseMapper;
      this.snapshotFactory = snapshotFactory;
      this.entityManager = entityManager;
   }
}

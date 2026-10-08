package com.auralink.api.v1.workflow;

import com.auralink.workflow.service.UserWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/workflows")
public class UserWorkflowController {
   private final UserWorkflowService workflowService;

   @PostMapping
   public ResponseEntity<WorkflowDetailResponse> create(@RequestBody WorkflowDefinitionRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED).body(this.workflowService.create(request));
   }

   @GetMapping
   public WorkflowPageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
      return this.workflowService.list(page, size);
   }

   @GetMapping("/{workflowId}")
   public WorkflowDetailResponse get(@PathVariable String workflowId) {
      return this.workflowService.get(workflowId);
   }

   @PutMapping("/{workflowId}")
   public WorkflowDetailResponse replace(@PathVariable String workflowId, @RequestBody WorkflowDefinitionRequest request) {
      return this.workflowService.replace(workflowId, request);
   }

   @DeleteMapping("/{workflowId}")
   public ResponseEntity<Void> delete(@PathVariable String workflowId) {
      this.workflowService.delete(workflowId);
      return ResponseEntity.noContent().build();
   }

   @PostMapping("/validate")
   public WorkflowValidationResponse validate(@RequestBody WorkflowDefinitionRequest request) {
      return this.workflowService.validate(request);
   }

   public UserWorkflowController(final UserWorkflowService workflowService) {
      this.workflowService = workflowService;
   }
}

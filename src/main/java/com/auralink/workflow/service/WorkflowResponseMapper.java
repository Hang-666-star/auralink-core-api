package com.auralink.workflow.service;

import com.auralink.api.v1.workflow.WorkflowDetailResponse;
import com.auralink.api.v1.workflow.WorkflowSummaryResponse;
import com.auralink.api.v1.workflow.WorkflowTimestampFormatter;
import com.auralink.entity.UserWorkflow;
import com.auralink.workflow.WorkflowLifecycleStatus;
import com.auralink.workflow.WorkflowNodeKind;
import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.auralink.workflow.graph.WorkflowGraphCodec;
import com.auralink.workflow.graph.WorkflowValidator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowResponseMapper {
   private final WorkflowGraphCodec codec;
   private final WorkflowValidator validator;

   public WorkflowDetailResponse detail(UserWorkflow entity) {
      StoredWorkflowDefinition stored = this.parse(entity);
      boolean conversionRequired = this.conversionRequired(entity, stored);
      return new WorkflowDetailResponse(
         entity.getPublicId(),
         entity.getName(),
         entity.getDescription(),
         entity.getSchemaVersion(),
         stored.graph(),
         stored.sourceModality(),
         stored.terminalModality(),
         stored.graph().nodes().size(),
         stored.graph().edges().size(),
         stored.operationSequence(),
         entity.getStatus(),
         conversionRequired,
         WorkflowTimestampFormatter.format(entity.getCreatedAt()),
         WorkflowTimestampFormatter.format(entity.getUpdatedAt())
      );
   }

   public WorkflowSummaryResponse summary(UserWorkflow entity) {
      StoredWorkflowDefinition stored = this.parse(entity);
      boolean conversionRequired = this.conversionRequired(entity, stored);
      return new WorkflowSummaryResponse(
         entity.getPublicId(),
         entity.getName(),
         entity.getDescription(),
         entity.getSchemaVersion(),
         stored.sourceModality(),
         stored.terminalModality(),
         stored.graph().nodes().size(),
         entity.getStatus(),
         conversionRequired,
         WorkflowTimestampFormatter.format(entity.getUpdatedAt()),
         WorkflowTimestampFormatter.format(entity.getCreatedAt())
      );
   }

   public StoredWorkflowDefinition parse(UserWorkflow entity) {
      CanonicalWorkflowGraph graph = this.codec.decode(entity.getGraphJson());
      if (!graph.nodes().isEmpty() && graph.nodes().get(0).kind() == WorkflowNodeKind.SOURCE) {
         List<WorkflowOperation> operations = graph.nodes()
            .stream()
            .filter(node -> node.kind() == WorkflowNodeKind.TRANSFORM)
            .map(node -> node.operation())
            .toList();
         return new StoredWorkflowDefinition(
            graph, graph.nodes().get(0).outputModality(), graph.nodes().get(graph.nodes().size() - 1).outputModality(), operations
         );
      } else {
         throw new IllegalStateException("Persisted workflow graph is not canonical");
      }
   }

   private boolean conversionRequired(UserWorkflow entity, StoredWorkflowDefinition stored) {
      return WorkflowLifecycleStatus.fromWire(entity.getStatus())
         .map(
            status -> status == WorkflowLifecycleStatus.DRAFT
               ? !this.validator.validateDraft(WorkflowStoredRequestFactory.from(entity, stored.graph())).valid()
               : !this.validator.validate(WorkflowStoredRequestFactory.from(entity, stored.graph())).valid()
         )
         .orElse(true);
   }

   public WorkflowResponseMapper(final WorkflowGraphCodec codec, final WorkflowValidator validator) {
      this.codec = codec;
      this.validator = validator;
   }
}

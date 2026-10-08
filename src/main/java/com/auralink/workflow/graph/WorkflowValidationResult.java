package com.auralink.workflow.graph;

import com.auralink.api.v1.error.ApiViolationDetail;
import java.util.List;

public record WorkflowValidationResult(
   boolean valid, String normalizedName, String normalizedDescription, WorkflowCanonicalization canonicalization, List<ApiViolationDetail> violations
) {
   public WorkflowValidationResult {
      violations = List.copyOf(violations);
   }
}

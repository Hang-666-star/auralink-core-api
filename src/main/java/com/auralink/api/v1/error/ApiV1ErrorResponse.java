package com.auralink.api.v1.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiV1ErrorResponse(
   Instant timestamp,
   int status,
   String code,
   String message,
   String path,
   String correlationId,
   Map<String, String> validationErrors,
   @JsonInclude(Include.NON_EMPTY) List<ApiViolationDetail> violations
) {
   public ApiV1ErrorResponse {
      validationErrors = Map.copyOf(validationErrors);
      violations = List.copyOf(violations);
   }
}

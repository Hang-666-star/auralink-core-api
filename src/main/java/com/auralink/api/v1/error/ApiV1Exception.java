package com.auralink.api.v1.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiV1Exception extends RuntimeException {
   private final HttpStatus status;
   private final ApiErrorCode code;
   private final List<ApiViolationDetail> violations;

   public ApiV1Exception(HttpStatus status, ApiErrorCode code, String publicMessage) {
      this(status, code, publicMessage, List.of());
   }

   public ApiV1Exception(HttpStatus status, ApiErrorCode code, String publicMessage, List<ApiViolationDetail> violations) {
      super(publicMessage);
      this.status = status;
      this.code = code;
      this.violations = List.copyOf(violations);
   }

   public HttpStatus getStatus() {
      return this.status;
   }

   public ApiErrorCode getCode() {
      return this.code;
   }

   public List<ApiViolationDetail> getViolations() {
      return this.violations;
   }
}

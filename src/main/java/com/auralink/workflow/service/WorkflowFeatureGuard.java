package com.auralink.workflow.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.WorkflowProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkflowFeatureGuard {
   private final WorkflowProperties properties;

   public void requireEnabled() {
      if (!this.properties.isEnabled()) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.WORKFLOWS_DISABLED, "工作流定义功能当前未启用");
      }
   }

   public WorkflowFeatureGuard(final WorkflowProperties properties) {
      this.properties = properties;
   }
}

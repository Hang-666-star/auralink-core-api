package com.auralink.creation;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.CreationExecutionProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CreationFeatureGuard {
   private final CreationExecutionProperties properties;

   public void requireEnabled() {
      if (!this.properties.isEnabled()) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CREATIONS_DISABLED, "创作执行功能当前未启用");
      }
   }

   public CreationFeatureGuard(final CreationExecutionProperties properties) {
      this.properties = properties;
   }
}

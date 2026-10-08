package com.auralink.creation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!catalog-postgres-creation-integration")
public class CreationExecutionBoundaryConfiguration {
   @Bean
   CreationExecutionBoundaryHook creationExecutionBoundaryHook() {
      return NoOpCreationExecutionBoundaryHook.INSTANCE;
   }
}

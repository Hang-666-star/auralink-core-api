package com.auralink.creation;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("catalog-postgres-creation-integration")
public class CreationLocalIntegrationConfiguration {
   @Bean
   CreationLocalIntegrationConfiguration.LocalControl localCreationControl() {
      return new CreationLocalIntegrationConfiguration.LocalControl();
   }

   @Bean
   CreationExecutionBoundaryHook creationExecutionBoundaryHook(CreationLocalIntegrationConfiguration.LocalControl control) {
      return boundary -> {
         if (control.nextFailure.compareAndSet(boundary, null)) {
            throw new CreationLocalIntegrationConfiguration.LocalInjectedCreationFailure(boundary);
         }
      };
   }

   static final class LocalControl {
      private final AtomicReference<CreationExecutionBoundary> nextFailure = new AtomicReference<>();

      void failAt(CreationExecutionBoundary boundary) {
         this.nextFailure.set(boundary);
      }

      String nextFailure() {
         CreationExecutionBoundary value = this.nextFailure.get();
         return value == null ? null : value.name();
      }
   }

   static final class LocalInjectedCreationFailure extends RuntimeException {
      LocalInjectedCreationFailure(CreationExecutionBoundary boundary) {
         super("local integration boundary: " + boundary.name());
      }
   }
}

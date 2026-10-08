package com.auralink.critic.service;

import com.auralink.catalogcritic.PostgresPaintingCritiqueService;
import com.auralink.config.properties.CriticProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaintingCritiqueRecoveryCoordinator {
   private final CriticProperties properties;
   private final PaintingCritiqueTaskStore store;
   private final ObjectProvider<PostgresPaintingCritiqueService> postgresService;

   public PaintingCritiqueRecoveryCoordinator(
      CriticProperties properties, PaintingCritiqueTaskStore store, ObjectProvider<PostgresPaintingCritiqueService> postgresService
   ) {
      this.properties = properties;
      this.store = store;
      this.postgresService = postgresService;
   }

   @Scheduled(fixedDelayString = "${auralink.critic.recovery-interval-ms:60000}")
   public void terminalizeAbandonedTasks() {
      if (this.properties.isConfigured()) {
         PostgresPaintingCritiqueService postgres = (PostgresPaintingCritiqueService)this.postgresService.getIfAvailable();
         if (postgres != null) {
            postgres.terminalizeExpiredRunning();
         } else {
            this.store.terminalizeExpiredRunning();
         }
      }
   }
}

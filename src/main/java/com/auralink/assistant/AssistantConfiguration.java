package com.auralink.assistant;

import com.auralink.catalogread.CatalogReadProperties;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Opt-in only: disabled installations never connect to Assistant tables or create storage. No DDL here. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auralink.assistant", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfiguration {
   @Bean AssistantStoragePolicy assistantStoragePolicy(Environment environment) { return AssistantStoragePolicy.read(environment::getProperty); }
   @Bean("assistantClock") Clock assistantClock() { return Clock.systemUTC(); }
   @Bean AssistantStore assistantStore(@Qualifier("catalogAuthDataSource") DataSource source,
      AssistantProperties properties, AssistantStoragePolicy policy, @Qualifier("assistantClock") Clock clock) {
      properties.validate();
      var store = new PostgresAssistantStore(source, clock, properties.getRequestReplayWindow(), properties.getMaxPendingPerSession(), policy);
      store.verifySchema();
      return store;
   }
   @Bean AssistantAttachmentStorage assistantAttachmentStorage(AssistantProperties properties, Environment environment) {
      properties.validate();
      Path root = Path.of(properties.getAttachmentRoot());
      if (!root.isAbsolute()) throw new IllegalArgumentException("Assistant attachment root must be absolute");
      root = root.normalize();
      for (String key : new String[]{"auralink.catalog-critic.private-media-root", "auralink.media-assets.managed-dir",
         "auralink.storage.upload-dir", "auralink.storage.audio-dir", "auralink.storage.legacy-frontend-audio-dir",
         "auralink.paintings.picture-dir"}) {
         rejectOverlap(root, environment.getProperty(key));
      }
      var catalog = Binder.get(environment).bind("auralink.catalog-read", Bindable.of(CatalogReadProperties.class));
      if (catalog.isBound()) for (var batch : catalog.get().getBatchMediaRoots()) rejectOverlap(root, batch.getRoot());
      return new AssistantAttachmentStorage(root, properties.getMaxUploadBytes(), properties.getMaxImagePixels());
   }
   @Bean AssistantAttachmentService assistantAttachmentService(AssistantStore store, AssistantAttachmentStorage storage,
      @Qualifier("assistantClock") Clock clock) { return new AssistantAttachmentService(store, storage, clock); }
   static void rejectOverlap(Path root, String shared) {
      if (shared == null || shared.isBlank()) return;
      Path existing = Path.of(shared).toAbsolutePath().normalize();
      if (root.startsWith(existing) || existing.startsWith(root) || overlapsResolved(root,existing)) {
         throw new IllegalArgumentException("Assistant attachments must not overlap existing media roots");
      }
   }
   private static boolean overlapsResolved(Path root,Path existing) {
      if (!Files.exists(existing)) return false;
      try {
         Path resolved=existing.toRealPath();
         return root.startsWith(resolved) || resolved.startsWith(root);
      } catch (IOException failure) {
         throw new IllegalArgumentException("Existing media root identity could not be verified");
      }
   }
}

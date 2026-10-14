package com.auralink.assistant;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.Files;
import java.nio.file.Path;

class AssistantConfigurationTest {
   @TempDir Path temp;
   private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(AssistantConfiguration.class);
   @Test void missingConfigurationDoesNotCreateAssistantBeansOrRequireAnyDataSource() {
      runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AssistantStore.class)
         .doesNotHaveBean(AssistantAttachmentStorage.class).doesNotHaveBean(AssistantProperties.class));
   }
   @Test void disabledIgnoresUnusedMalformedAssistantLimitsAndDoesNotCreateDirectory() {
      Path root=temp.resolve("not-created");
      runner.withPropertyValues("auralink.assistant.enabled=false","auralink.assistant.attachment-root="+root,
         "auralink.assistant.request-replay-window=not-a-duration").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AssistantStore.class);
            assertThat(Files.exists(root)).isFalse();
         });
   }
   @Test void enabledWithoutExplicitCatalogAuthenticationStorageFailsClosed() {
      runner.withPropertyValues("auralink.assistant.enabled=true").run(context -> assertThat(context).hasFailed());
   }
   @Test void configuredPrivateRootMustNotContainOrSitInsideSharedMedia() {
      assertThatThrownBy(() -> AssistantConfiguration.rejectOverlap(temp.resolve("media/assistant"),temp.resolve("media").toString())).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> AssistantConfiguration.rejectOverlap(temp,temp.resolve("media").toString())).isInstanceOf(IllegalArgumentException.class);
      AssistantConfiguration.rejectOverlap(temp.resolve("assistant"),temp.resolve("media").toString());
   }
}

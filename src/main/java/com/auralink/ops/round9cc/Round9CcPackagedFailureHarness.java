package com.auralink.ops.round9cc;

import com.auralink.Application;
import com.auralink.creation.CreationExecutionBoundary;
import com.auralink.provider.artifact.ProviderArtifactStagingService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;

public final class Round9CcPackagedFailureHarness {
   private Round9CcPackagedFailureHarness() {
   }

   public static void main(String[] args) {
      Round9CcPackagedFailureHarness.Launch launch = Round9CcPackagedFailureHarness.Launch.parse(args);
      ConfigurableApplicationContext context = start(launch);
      boolean importedPrivateEnvironment = importsBackendEnvironment(context);
      if (importedPrivateEnvironment) {
         context.close();
         throw new IllegalStateException("ROUND 9C-C environment boundary failed");
      }

      if (launch.scenario() == Round9CcScenario.NORMAL_COMPLETION) {
         completeNormalCompletionAndClose(context, launch);
      } else {
         if (launch.scenario().isBatch1()) {
            Round9CcBatch1SeedCoordinator batch1 = (Round9CcBatch1SeedCoordinator)context.getBean(Round9CcBatch1SeedCoordinator.class);
            switch (launch.phase()) {
               case SEED:
                  try {
                     batch1.seed(launch);
                  } finally {
                     context.close();
                  }

                  return;
               case INITIAL:
                  batch1.requireSeeded(launch);
                  batch1.beginInitialExecution(launch);
                  break;
               case RECOVERY:
                  try {
                     batch1.verifyRecovered(launch);
                  } finally {
                     context.close();
                  }

                  return;
            }
         }

         System.out.println("ROUND9CC_INSTANCE_READY");
      }
   }

   static ConfigurableApplicationContext start(Round9CcPackagedFailureHarness.Launch launch) {
      Round9CcPackagedFailureHarness.HarnessState state = new Round9CcPackagedFailureHarness.HarnessState(launch);
      List<String> arguments = startupArguments(launch);
      return new SpringApplicationBuilder(new Class[]{Application.class})
         .initializers(new ApplicationContextInitializer[]{new Round9CcPackagedFailureHarness.HarnessConfiguration(state)})
         .web(WebApplicationType.SERVLET)
         .run(arguments.toArray(String[]::new));
   }

   static List<String> startupArguments(Round9CcPackagedFailureHarness.Launch launch) {
      Round9CcFixture fixture = launch.fixture();
      Path root = fixture.root();
      List<String> arguments = new ArrayList<>(
         List.of(
            "--spring.config.import=optional:file:" + fixture.propertiesFile(),
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.datasource.url=jdbc:sqlite:" + root.resolve("db/fixture.db"),
            "--spring.jpa.hibernate.ddl-auto=none",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--auralink.jwt.secret=round9cc-fixture-only-secret",
            "--auralink.workflows.enabled=true",
            "--auralink.creations.enabled=true",
            "--auralink.creations.dispatch-delay=3600000",
            "--auralink.creation-providers.enabled=false",
            "--auralink.creation-providers.mock-adapters-enabled=false",
            "--auralink.creation-providers.staging-dir=" + root.resolve("provider-staging"),
            "--auralink.media-assets.managed-dir=" + root.resolve("managed"),
            "--auralink.paintings.picture-dir=" + root.resolve("catalog-unavailable"),
            "--auralink.paintings.metadata-csv-path=" + root.resolve("catalog-unavailable.csv"),
            "--auralink.paintings.import-enabled=false",
            "--auralink.storage.upload-dir=" + root.resolve("uploads"),
            "--auralink.storage.audio-dir=" + root.resolve("audio"),
            "--auralink.storage.legacy-frontend-audio-dir=" + root.resolve("legacy-audio")
         )
      );
      if (launch.scenario().isBatch1()) {
         arguments.add("--auralink.creations.lease-duration=2s");
         arguments.add("--auralink.creations.heartbeat-interval=1s");
         arguments.add("--auralink.creations.recovery-grace=1s");
         arguments.add("--auralink.creations.recovery-interval=1s");
         arguments.add("--auralink.creations.recovery-fence-lease=300s");
         arguments.add("--auralink.creations.startup-max-batches=2");
      }

      return List.copyOf(arguments);
   }

   static Round9CcNormalCompletionCoordinator.Completion completeNormalCompletion(
      ConfigurableApplicationContext context, Round9CcPackagedFailureHarness.Launch launch
   ) {
      if (launch.scenario() != Round9CcScenario.NORMAL_COMPLETION) {
         throw new IllegalArgumentException("ROUND 9C-C normal completion scenario is required");
      } else {
         return new Round9CcNormalCompletionCoordinator(
               context, (Round9CcPackagedFailureHarness.HarnessState)context.getBean(Round9CcPackagedFailureHarness.HarnessState.class)
            )
            .run(launch);
      }
   }

   static Round9CcNormalCompletionCoordinator.Completion completeNormalCompletionAndClose(
      ConfigurableApplicationContext context, Round9CcPackagedFailureHarness.Launch launch
   ) {
      try {
         return completeNormalCompletion(context, launch);
      } finally {
         context.close();
      }
   }

   static void writePrivate(Path file, String content) {
      try {
         if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Round9CcFixture.setPrivateFile(file);
         } else {
            throw new IOException("runtime file already exists");
         }
      } catch (IOException exception) {
         throw new IllegalStateException("ROUND 9C-C runtime metadata could not be recorded");
      }
   }

   private static boolean importsBackendEnvironment(ConfigurableApplicationContext context) {
      for (PropertySource<?> source : context.getEnvironment().getPropertySources()) {
         String name = source.getName().replace('\\', '/');
         if (name.endsWith("/backend/.env") || name.contains("/backend/.env[")) {
            return true;
         }
      }

      return false;
   }

   static IllegalArgumentException manifestMismatch() {
      return new IllegalArgumentException("ROUND9CC_ERROR:MANIFEST_LAUNCH_MISMATCH");
   }

   static final class HarnessConfiguration implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      private final Round9CcPackagedFailureHarness.HarnessState state;

      HarnessConfiguration(Round9CcPackagedFailureHarness.HarnessState state) {
         this.state = state;
      }

      public void initialize(ConfigurableApplicationContext context) {
         if (context.getBeanFactory() instanceof DefaultListableBeanFactory beanFactory) {
            beanFactory.registerSingleton("round9CcHarnessState", this.state);
            RootBeanDefinition runtimeEvidence = new RootBeanDefinition(Round9CcPackagedFailureHarness.RuntimeEvidenceListener.class);
            runtimeEvidence.setInstanceSupplier(() -> new Round9CcPackagedFailureHarness.RuntimeEvidenceListener(this.state));
            beanFactory.registerBeanDefinition("round9CcRuntimeEvidenceListener", runtimeEvidence);
            RootBeanDefinition hook = new RootBeanDefinition(Round9CcBarrierExecutionBoundaryHook.class);
            hook.setPrimary(true);
            hook.setInstanceSupplier(this.state::hook);
            beanFactory.registerBeanDefinition("round9CcBarrierExecutionBoundaryHook", hook);
            RootBeanDefinition adapter = new RootBeanDefinition(Round9CcMockCreationProviderAdapter.class);
            adapter.setInstanceSupplier(
               () -> new Round9CcMockCreationProviderAdapter(
                  (ProviderArtifactStagingService)beanFactory.getBean(ProviderArtifactStagingService.class), this.state.hook(), this.state.journal()
               )
            );
            beanFactory.registerBeanDefinition("round9CcMockCreationProviderAdapter", adapter);
            if (this.state.scenario().isBatch1()) {
               RootBeanDefinition batch1 = new RootBeanDefinition(Round9CcBatch1SeedCoordinator.class);
               batch1.setAutowireMode(3);
               beanFactory.registerBeanDefinition("round9CcBatch1SeedCoordinator", batch1);
            }
         } else {
            throw new IllegalStateException("ROUND 9C-C harness context is unsupported");
         }
      }
   }

   static final class HarnessState {
      private final Round9CcPackagedFailureHarness.Launch launch;
      private final Round9CcMockJournal journal;
      private final Round9CcBarrierExecutionBoundaryHook hook;
      private final Round9CcScenario scenario;

      HarnessState(Round9CcPackagedFailureHarness.Launch launch) {
         this.launch = launch;
         this.scenario = launch.scenario();
         this.journal = new Round9CcMockJournal(launch.fixture(), this.scenario.name(), launch.instance());
         Set<CreationExecutionBoundary> selected = launch.failpoint() == null ? Set.of() : Set.of(launch.failpoint());
         this.hook = new Round9CcBarrierExecutionBoundaryHook(launch.fixture(), launch.instance(), selected, launch.timeout(), this.journal);
      }

      Round9CcMockJournal journal() {
         return this.journal;
      }

      Round9CcBarrierExecutionBoundaryHook hook() {
         return this.hook;
      }

      Round9CcScenario scenario() {
         return this.scenario;
      }

      void recordStarted(int port) {
         if (port >= 1 && port <= 65535) {
            Round9CcPackagedFailureHarness.writePrivate(this.launch.fixture().runtimeFile(this.launch.instance(), "port"), port + "\n");
            Round9CcPackagedFailureHarness.writePrivate(
               this.launch.fixture().runtimeFile(this.launch.instance(), "boundary"), "NO_BACKEND_ENV\nMOCK_ONLY_NO_REAL_PROVIDER\n"
            );
            Round9CcPackagedFailureHarness.writePrivate(this.launch.fixture().runtimeFile(this.launch.instance(), "role"), this.launch.role() + "\n");
         } else {
            throw new IllegalStateException("ROUND 9C-C Harness port is invalid");
         }
      }
   }

   record Launch(
      Round9CcFixture fixture, String instance, Round9CcScenario scenario, Round9CcRunPhase phase, CreationExecutionBoundary failpoint, Duration timeout
   ) {
      static Round9CcPackagedFailureHarness.Launch parse(String[] arguments) {
         Map<String, String> values = parseArguments(arguments);
         Round9CcFixture fixture = Round9CcFixture.validate(Path.of(require(values, "fixture-root")));
         String instance = require(values, "instance");
         Round9CcFixture.validateInstance(instance);
         Round9CcScenario scenario = Round9CcScenario.require(require(values, "scenario"));
         Round9CcRunPhase phase = Round9CcRunPhase.require(values.get("phase"));
         if (!scenario.supports(phase)) {
            throw Round9CcPackagedFailureHarness.manifestMismatch();
         }

         Duration timeout;
         try {
            timeout = Duration.ofSeconds(Long.parseLong(values.getOrDefault("failpoint-timeout-seconds", "30")));
         } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ROUND 9C-C launch arguments are invalid");
         }

         requirePrivateProperties(fixture.propertiesFile());
         requireScenarioManifest(fixture, scenario);
         return new Round9CcPackagedFailureHarness.Launch(fixture, instance, scenario, phase, scenario.failpointFor(phase), timeout);
      }

      String role() {
         return this.scenario.roleFor(this.phase);
      }

      private static Map<String, String> parseArguments(String[] arguments) {
         LinkedHashMap<String, String> values = new LinkedHashMap<>();
         if (arguments == null) {
            throw new IllegalArgumentException("ROUND 9C-C launch arguments are invalid");
         }

         String[] var2 = arguments;
         int var3 = var2.length;
         int var4 = 0;

         while (var4 < var3) {
            String argument = var2[var4];
            if (argument != null && argument.startsWith("--") && argument.indexOf(61) >= 3) {
               int separator = argument.indexOf(61);
               String key = argument.substring(2, separator);
               String value = argument.substring(separator + 1);
               if (key.matches("[a-z-]{1,40}") && !value.isBlank() && values.putIfAbsent(key, value) == null) {
                  if (!Set.of("fixture-root", "instance", "scenario", "phase", "failpoint-timeout-seconds").contains(key)) {
                     throw Round9CcPackagedFailureHarness.manifestMismatch();
                  }

                  var4++;
                  continue;
               }

               throw new IllegalArgumentException("ROUND 9C-C launch arguments are invalid");
            }

            throw new IllegalArgumentException("ROUND 9C-C launch arguments are invalid");
         }

         return Map.copyOf(values);
      }

      private static String require(Map<String, String> values, String key) {
         String value = values.get(key);
         if (value != null && !value.isBlank()) {
            return value;
         } else {
            throw new IllegalArgumentException("ROUND 9C-C launch arguments are invalid");
         }
      }

      private static void requirePrivateProperties(Path properties) {
         try {
            if (!Files.isSymbolicLink(properties) && Files.isRegularFile(properties, LinkOption.NOFOLLOW_LINKS)) {
               Round9CcFixture.setPrivateFile(properties);
            } else {
               throw new IllegalArgumentException("ROUND 9C-C fixture properties are invalid");
            }
         } catch (IOException exception) {
            throw new IllegalArgumentException("ROUND 9C-C fixture properties are invalid");
         }
      }

      private static void requireScenarioManifest(Round9CcFixture fixture, Round9CcScenario scenario) {
         try {
            Path manifest = fixture.manifestFile();
            if (!Files.isSymbolicLink(manifest) && Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
               Properties values = new Properties();

               try (InputStream input = Files.newInputStream(manifest)) {
                  values.load(input);
               }

               for (Entry<String, String> expected : scenario.manifestValues().entrySet()) {
                  if (!expected.getValue().equals(values.getProperty(expected.getKey()))) {
                     throw Round9CcPackagedFailureHarness.manifestMismatch();
                  }
               }
            } else {
               throw new IllegalArgumentException("ROUND 9C-C scenario manifest is invalid");
            }
         } catch (IOException exception) {
            throw new IllegalArgumentException("ROUND 9C-C scenario manifest is invalid");
         }
      }
   }

   static final class RuntimeEvidenceListener implements ApplicationListener<WebServerInitializedEvent> {
      private final Round9CcPackagedFailureHarness.HarnessState state;

      RuntimeEvidenceListener(Round9CcPackagedFailureHarness.HarnessState state) {
         this.state = state;
      }

      public void onApplicationEvent(WebServerInitializedEvent event) {
         if (event.getApplicationContext() instanceof ConfigurableApplicationContext context
            && !Round9CcPackagedFailureHarness.importsBackendEnvironment(context)) {
            this.state.recordStarted(event.getWebServer().getPort());
         } else {
            throw new IllegalStateException("ROUND 9C-C environment boundary failed");
         }
      }
   }
}

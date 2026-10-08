package com.auralink.ops.round51;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class Round51ActivationCommand {
   static final Path SERVER_LOCAL_ROOT = Path.of("/root/autodl-tmp/auralink");
   static final String CONFIRMATION = "ACTIVATE_AURALINK_2_0_CATALOG";
   private static final String CONTROLLED_FAILURE_SUMMARY = "Controlled activation safety check failed";
   private static final String PREFLIGHT_IO_FAILURE_SUMMARY = "Activation preflight could not verify required local resources";
   private static final String CONTEXT_FAILURE_SUMMARY = "Dedicated non-web activation context could not be initialized";
   private static final String EXECUTION_FAILURE_SUMMARY = "Controlled activation execution failed";

   private Round51ActivationCommand() {
   }

   public static void main(String[] args) {
      System.exit(run(args));
   }

   static int run(String[] args) {
      String unexpectedFailureCode = "ACTIVATION_PREFLIGHT_RUNTIME_FAILURE";
      String unexpectedFailureSummary = "Activation preflight could not verify required local resources";

      try {
         Path projectRoot = parseProjectRoot(args);
         Round51ActivationCommand.VerifiedPaths paths = verifyServerLocalProject(projectRoot, Path.of("").toRealPath());
         if (!"ACTIVATE_AURALINK_2_0_CATALOG".equals(System.getenv("AURALINK_ROUND51_CONFIRM"))) {
            throw failure("ACTIVATION_CONFIRMATION_REQUIRED");
         }

         System.out.println("SERVER_LOCAL_ROOT_VERIFIED");
         String[] controlledArguments = new String[]{
            "--spring.config.import=optional:file:" + paths.environmentFile() + "[.properties]",
            "--spring.datasource.url=jdbc:sqlite:" + paths.database(),
            "--spring.jpa.hibernate.ddl-auto=none",
            "--spring.flyway.enabled=false",
            "--spring.sql.init.mode=never",
            "--spring.main.web-application-type=none",
            "--spring.datasource.hikari.connection-init-sql=PRAGMA foreign_keys=ON",
            "--auralink.paintings.import-enabled=false",
            "--auralink.paintings.metadata-csv-path=" + paths.catalogCsv(),
            "--auralink.paintings.picture-dir=" + paths.pictureDirectory(),
            "--auralink.media-assets.managed-dir=" + paths.backend().resolve("temp_uploads/media-assets"),
            "--auralink.storage.upload-dir=" + paths.backend().resolve("temp_uploads"),
            "--auralink.storage.audio-dir=" + paths.backend().resolve("temp_uploads/activation-audio"),
            "--auralink.storage.legacy-frontend-audio-dir=" + paths.backend().resolve("temp_uploads/activation-legacy-audio")
         };
         unexpectedFailureCode = "ACTIVATION_CONTEXT_INITIALIZATION_FAILED";
         unexpectedFailureSummary = "Dedicated non-web activation context could not be initialized";
         ConfigurableApplicationContext context = startActivationContext(controlledArguments);

         try {
            unexpectedFailureCode = "ACTIVATION_EXECUTION_FAILED";
            unexpectedFailureSummary = "Controlled activation execution failed";
            Round51ActivationResult result = ((Round51ActivationCoordinator)context.getBean(Round51ActivationCoordinator.class)).activate();
            if (result.state() == Round51ActivationState.ALREADY_ACTIVATED_HEALTHY) {
               System.out.println("ALREADY_ACTIVATED_AND_HEALTHY");
            } else {
               if (result.state() != Round51ActivationState.ACTIVATED_NOW) {
                  throw failure("UNEXPECTED_ACTIVATION_RESULT");
               }

               System.out.println("ROUND51_ACTIVATION_COMPLETED");
            }

            System.out.println("ROUND51_DATABASE_ACTIVATION_VERIFIED");
         } catch (Throwable var10) {
            if (context != null) {
               try {
                  context.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (context != null) {
            context.close();
         }

         return 0;
      } catch (Round51ActivationException exception) {
         reportSafeFailure(exception.getCode(), "Controlled activation safety check failed");
         return 2;
      } catch (IOException exception) {
         reportSafeFailure("ACTIVATION_PREFLIGHT_IO_FAILURE", "Activation preflight could not verify required local resources");
         return 2;
      } catch (RuntimeException exception) {
         reportSafeFailure(unexpectedFailureCode, unexpectedFailureSummary);
         return 2;
      }
   }

   static ConfigurableApplicationContext startActivationContext(String... controlledArguments) {
      return new SpringApplicationBuilder(new Class[]{Round51ActivationContextConfiguration.class})
         .web(WebApplicationType.NONE)
         .registerShutdownHook(false)
         .run(controlledArguments);
   }

   private static void reportSafeFailure(String code, String summary) {
      String safeCode = code != null && code.matches("[A-Z][A-Z0-9_]{0,79}") ? code : "UNEXPECTED_SAFE_FAILURE";
      System.err.println("ROUND51_ACTIVATION_ERROR_CLASS=" + safeCode);
      System.err.println("ROUND51_ACTIVATION_ERROR_SUMMARY=" + summary);
      System.err.println("ROUND51_ACTIVATION_REFUSED:" + safeCode);
   }

   static Round51ActivationCommand.VerifiedPaths verifyServerLocalProject(Path requestedRoot, Path workingDirectory) throws IOException {
      if (Files.isSymbolicLink(SERVER_LOCAL_ROOT)) {
         throw failure("SERVER_LOCAL_ROOT_REQUIRED");
      } else {
         Path expected = SERVER_LOCAL_ROOT.toRealPath();
         Path actual = requestedRoot.toRealPath();
         Path cwd = workingDirectory.toRealPath();
         if (expected.equals(SERVER_LOCAL_ROOT) && actual.equals(expected) && cwd.equals(expected)) {
            Path backend = requireDirectory(actual.resolve("backend"), "BACKEND_DIRECTORY_REQUIRED");
            Path database = contained(actual, requireRegularFile(backend.resolve("auralink.db"), "LIVE_DATABASE_REQUIRED"));
            Path environment = contained(actual, requireRegularFile(backend.resolve(".env"), "BACKEND_ENV_REQUIRED"));
            contained(actual, requireRegularFile(backend.resolve("src/main/resources/db/migration/V1__legacy_schema_baseline.sql"), "MIGRATION_FILES_REQUIRED"));
            contained(
               actual,
               requireRegularFile(backend.resolve("src/main/resources/db/migration/V2__create_auralink_2_0_foundation.sql"), "MIGRATION_FILES_REQUIRED")
            );
            Path csv = contained(actual, requireRegularFile(actual.resolve("frontend/public/data/paintings.csv"), "OFFICIAL_CATALOG_REQUIRED"));
            Path pictures = contained(actual, requireDirectory(backend.resolve("picture"), "OFFICIAL_IMAGES_REQUIRED"));
            return new Round51ActivationCommand.VerifiedPaths(actual, backend, database, environment, csv, pictures);
         } else {
            throw failure("SERVER_LOCAL_ROOT_REQUIRED");
         }
      }
   }

   private static Path contained(Path root, Path candidate) {
      if (!candidate.startsWith(root)) {
         throw failure("PROJECT_PATH_ESCAPE_REFUSED");
      } else {
         return candidate;
      }
   }

   private static Path parseProjectRoot(String[] args) {
      if (args.length == 1 && args[0].startsWith("--project-root=")) {
         String value = args[0].substring("--project-root=".length());
         if (value.isBlank()) {
            throw failure("EXACT_PROJECT_ROOT_ARGUMENT_REQUIRED");
         } else {
            return Path.of(value);
         }
      } else {
         throw failure("EXACT_PROJECT_ROOT_ARGUMENT_REQUIRED");
      }
   }

   private static Path requireRegularFile(Path path, String code) throws IOException {
      if (!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
         return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
      } else {
         throw failure(code);
      }
   }

   private static Path requireDirectory(Path path, String code) throws IOException {
      if (!Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
         return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
      } else {
         throw failure(code);
      }
   }

   private static Round51ActivationException failure(String code) {
      return new Round51ActivationException(code, "Activation safety condition was not met");
   }

   record VerifiedPaths(Path projectRoot, Path backend, Path database, Path environmentFile, Path catalogCsv, Path pictureDirectory) {
   }
}

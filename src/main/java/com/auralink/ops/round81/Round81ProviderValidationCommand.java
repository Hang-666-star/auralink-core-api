package com.auralink.ops.round81;

import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderSafeDiagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class Round81ProviderValidationCommand {
   static final Path SERVER_LOCAL_ROOT = Path.of("/root/autodl-tmp/auralink");
   static final Path PRIVATE_RUN_ROOT = Path.of("/root/auralink_provider_validation_runs");
   private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
   private static final String MOCK_TOKEN = "LOCAL_LOOPBACK_ONLY";

   private Round81ProviderValidationCommand() {
   }

   public static void main(String[] args) {
      System.exit(run(args));
   }

   static int run(String[] args) {
      try {
         Round81ProviderValidationCommand.ParsedArguments parsed = parseArguments(args);
         Round81ProviderValidationCommand.VerifiedRuntime runtime = parsed.mock() ? verifyMockRuntime(parsed) : verifyLiveRuntime(parsed);
         String[] controlledArguments = controlledArguments(runtime, parsed);
         ConfigurableApplicationContext context = startValidationContext(controlledArguments);

         try {
            Round81ProviderValidationCoordinator coordinator = (Round81ProviderValidationCoordinator)context.getBean(Round81ProviderValidationCoordinator.class);
            if (parsed.mode() == Round81ProviderValidationCommand.ValidationMode.DRY_RUN) {
               coordinator.dryRun(parsed.operation());
               System.out.println("DRY_RUN_ZERO_MUTATION");
               System.out.println("DRY_RUN_OK");
            } else {
               Round81RetainedResult retained = coordinator.validate(parsed.operation(), runtime.runDirectory());
               System.out.println(retained.structuralState());
               System.out.println(retained.reviewState());
               System.out.println("PROVIDER_CALL_COUNTS_VERIFIED");
               System.out.println("PROVIDER_STAGING_CLEANED");
            }
         } catch (Throwable var8) {
            if (context != null) {
               try {
                  context.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (context != null) {
            context.close();
         }

         return 0;
      } catch (Round81ValidationException exception) {
         reportSafeFailure(exception.code());
         return 2;
      } catch (ProviderExecutionException exception) {
         reportSafeFailure(exception.category().name());
         reportSafeDiagnostic(exception.safeDiagnostic());
         return 2;
      } catch (RuntimeException exception) {
         reportSafeFailure("VALIDATION_CONTEXT_FAILED");
         return 3;
      }
   }

   static ConfigurableApplicationContext startValidationContext(String... controlledArguments) {
      return new SpringApplicationBuilder(new Class[]{Round81ProviderValidationContextConfiguration.class})
         .web(WebApplicationType.NONE)
         .registerShutdownHook(false)
         .logStartupInfo(false)
         .run(controlledArguments);
   }

   private static Round81ProviderValidationCommand.ParsedArguments parseArguments(String[] args) {
      String mode = null;
      String operation = null;
      boolean mock = false;

      for (String argument : args) {
         if (argument.startsWith("--mode=")) {
            if (mode != null) {
               throw invalidArguments();
            }

            mode = argument.substring("--mode=".length());
         } else if (argument.startsWith("--operation=")) {
            if (operation != null) {
               throw invalidArguments();
            }

            operation = argument.substring("--operation=".length());
         } else {
            if (!"--mock".equals(argument)) {
               throw invalidArguments();
            }

            if (mock) {
               throw invalidArguments();
            }

            mock = true;
         }
      }

      if (mode != null && operation != null) {
         Round81ProviderValidationCommand.ValidationMode parsedMode = switch (mode) {
            case "dry-run" -> Round81ProviderValidationCommand.ValidationMode.DRY_RUN;
            case "validate" -> Round81ProviderValidationCommand.ValidationMode.VALIDATE;
            default -> throw invalidArguments();
         };
         return new Round81ProviderValidationCommand.ParsedArguments(parsedMode, Round81ValidationOperation.fromToken(operation), mock);
      } else {
         throw invalidArguments();
      }
   }

   private static Round81ProviderValidationCommand.VerifiedRuntime verifyLiveRuntime(Round81ProviderValidationCommand.ParsedArguments parsed) {
      try {
         Path cwd = Path.of("").toRealPath();
         if (!Files.isSymbolicLink(SERVER_LOCAL_ROOT) && Files.isDirectory(SERVER_LOCAL_ROOT, LinkOption.NOFOLLOW_LINKS)) {
            Path realRoot = SERVER_LOCAL_ROOT.toRealPath();
            if (realRoot.equals(SERVER_LOCAL_ROOT) && cwd.equals(realRoot)) {
               FileStore store = Files.getFileStore(realRoot);
               String filesystem = store.type().toLowerCase(Locale.ROOT);
               if (!filesystem.contains("fuse") && !filesystem.contains("sshfs")) {
                  Path environmentFile = requireRegularFile(realRoot.resolve("backend/.env"), "BACKEND_ENV_REQUIRED");
                  Path jar = requireRegularFile(realRoot.resolve("backend/target/auralink-backend-0.0.1-SNAPSHOT.jar"), "PACKAGED_JAR_REQUIRED");
                  String expected = requireCommit(System.getenv("AURALINK_ROUND81_EXPECTED_COMMIT"));
                  String actual = runGit(realRoot, "rev-parse", "HEAD");
                  if (!expected.equals(actual)) {
                     throw failure("REVIEWED_COMMIT_MISMATCH");
                  }

                  if (!runGit(realRoot, "status", "--porcelain=v1", "--untracked-files=normal").isEmpty()) {
                     throw failure("WORKTREE_NOT_CLEAN");
                  }

                  if (parsed.mode() == Round81ProviderValidationCommand.ValidationMode.VALIDATE) {
                     requireConfirmation(parsed.operation());
                  }

                  System.out.println("SERVER_LOCAL_ROOT_VERIFIED");
                  System.out.println("REVIEWED_COMMIT_VERIFIED=" + actual);
                  System.out.println("PROVIDER_VALIDATION_WORKTREE_CLEAN");
                  Path runDirectory = parsed.mode() == Round81ProviderValidationCommand.ValidationMode.VALIDATE
                     ? requiredEnvironmentPath("AURALINK_ROUND81_RUN_DIR")
                     : null;
                  if (runDirectory == null || runDirectory.startsWith(PRIVATE_RUN_ROOT) && PRIVATE_RUN_ROOT.equals(runDirectory.getParent())) {
                     return new Round81ProviderValidationCommand.VerifiedRuntime(environmentFile, jar, runDirectory, false);
                  } else {
                     throw failure("PRIVATE_RUN_DIRECTORY_INVALID");
                  }
               } else {
                  throw failure("SSHFS_EXECUTION_REFUSED");
               }
            } else {
               throw failure("SERVER_LOCAL_ROOT_REQUIRED");
            }
         } else {
            throw failure("SERVER_LOCAL_ROOT_REQUIRED");
         }
      } catch (IOException exception) {
         throw new Round81ValidationException("SERVER_LOCAL_PREFLIGHT_FAILED", "Server-local validation preflight failed", exception);
      }
   }

   private static Round81ProviderValidationCommand.VerifiedRuntime verifyMockRuntime(Round81ProviderValidationCommand.ParsedArguments parsed) {
      if (!"LOCAL_LOOPBACK_ONLY".equals(System.getenv("AURALINK_ROUND81_MOCK_MODE"))) {
         throw failure("MOCK_MODE_REFUSED");
      }

      String base = System.getenv("AURALINK_ROUND81_MOCK_BASE_URL");
      if (base != null && !base.isBlank()) {
         if (parsed.mode() == Round81ProviderValidationCommand.ValidationMode.VALIDATE) {
            requireConfirmation(parsed.operation());
         }

         Path runDirectory = parsed.mode() == Round81ProviderValidationCommand.ValidationMode.VALIDATE
            ? requiredEnvironmentPath("AURALINK_ROUND81_RUN_DIR")
            : null;
         Path staging = requiredEnvironmentPath("AURALINK_PROVIDER_STAGING_DIR");
         Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
         if ((runDirectory == null || runDirectory.normalize().startsWith(temporaryRoot)) && staging.normalize().startsWith(temporaryRoot)) {
            return new Round81ProviderValidationCommand.VerifiedRuntime(null, null, runDirectory, true);
         } else {
            throw failure("MOCK_PATH_OUTSIDE_TEMP");
         }
      } else {
         throw failure("MOCK_ENDPOINT_INVALID");
      }
   }

   private static String[] controlledArguments(
      Round81ProviderValidationCommand.VerifiedRuntime runtime, Round81ProviderValidationCommand.ParsedArguments parsed
   ) {
      List<String> arguments = new ArrayList<>();
      arguments.add("--spring.main.web-application-type=none");
      arguments.add("--spring.main.banner-mode=off");
      arguments.add("--spring.jmx.enabled=false");
      arguments.add("--logging.level.root=OFF");
      arguments.add("--auralink.creation-providers.enabled=true");
      if (!runtime.mock()) {
         arguments.add("--spring.config.import=optional:file:" + runtime.environmentFile() + "[.properties]");
      } else {
         String mockBase = System.getenv("AURALINK_ROUND81_MOCK_BASE_URL");
         arguments.add("--spring.config.location=optional:classpath:/round81-provider-validation-mock.properties");
         arguments.add("--auralink.round81.mock-mode=LOCAL_LOOPBACK_ONLY");
         arguments.add("--auralink.round81.mock-base-url=" + mockBase);
         arguments.add("--auralink.creation-providers.staging-dir=" + requiredEnvironmentPath("AURALINK_PROVIDER_STAGING_DIR"));
         arguments.add("--auralink.providers.seedream.api-key=round81-mock-seedream-key");
         arguments.add("--auralink.providers.seedream.base-url=https://ark.cn-beijing.volces.com/api/v3");
         arguments.add("--auralink.providers.seedream.model=round81-mock-seedream-model");
         arguments.add("--auralink.providers.qwen.api-key=round81-mock-qwen-key");
         arguments.add("--auralink.providers.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1");
         arguments.add("--auralink.providers.qwen.model=qwen3-vl-plus");
         arguments.add("--auralink.providers.painting-music.base-url=" + mockBase);
         arguments.add("--auralink.providers.painting-music.output-root=" + requiredEnvironmentPath("AURALINK_VMM_OUTPUT_DIR"));
      }

      return arguments.toArray(String[]::new);
   }

   private static void requireConfirmation(Round81ValidationOperation operation) {
      if (!operation.confirmation().equals(System.getenv("AURALINK_ROUND81_CONFIRM"))) {
         throw failure("OPERATION_CONFIRMATION_REQUIRED");
      }
   }

   private static Path requiredEnvironmentPath(String name) {
      String raw = System.getenv(name);
      if (raw != null && !raw.isBlank()) {
         Path configured = Path.of(raw);
         if (!configured.isAbsolute()) {
            throw failure("VALIDATION_PATH_CONFIGURATION_INVALID");
         } else {
            return configured.normalize();
         }
      } else {
         throw failure("VALIDATION_PATH_CONFIGURATION_MISSING");
      }
   }

   private static Path requireRegularFile(Path path, String code) throws IOException {
      if (!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
         return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
      } else {
         throw failure(code);
      }
   }

   private static String requireCommit(String value) {
      if (value != null && COMMIT.matcher(value).matches()) {
         return value;
      } else {
         throw failure("EXPECTED_COMMIT_REQUIRED");
      }
   }

   private static String runGit(Path root, String... arguments) throws IOException {
      List<String> command = new ArrayList<>();
      command.add("git");
      command.add("-C");
      command.add(root.toString());
      command.addAll(List.of(arguments));
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

      byte[] bytes;
      try {
         bytes = process.getInputStream().readNBytes(1048576);
         if (!process.waitFor(15L, TimeUnit.SECONDS)) {
            process.destroy();
            throw failure("GIT_GUARD_FAILED");
         }
      } catch (InterruptedException exception) {
         Thread.currentThread().interrupt();
         throw new IOException("Git guard was interrupted", exception);
      }

      if (process.exitValue() == 0 && process.getInputStream().read() == -1) {
         return new String(bytes, StandardCharsets.UTF_8).trim();
      } else {
         throw failure("GIT_GUARD_FAILED");
      }
   }

   private static void reportSafeFailure(String code) {
      String safeCode = code != null && code.matches("[A-Z][A-Z0-9_]{0,95}") ? code : "UNEXPECTED_SAFE_FAILURE";
      System.err.println("ROUND81_VALIDATION_ERROR_CODE=" + safeCode);
   }

   private static void reportSafeDiagnostic(ProviderSafeDiagnostic<?, ?, ?> diagnostic) {
      if (diagnostic != null) {
         String stage = diagnostic.validationStage().name();
         String code = diagnostic.validationCode().name();
         if (stage.matches("[A-Z][A-Z0-9_]{0,95}") && code.matches("[A-Z][A-Z0-9_]{0,95}")) {
            System.err.println("ROUND81_RESPONSE_VALIDATION_STAGE=" + stage);
            System.err.println("ROUND81_RESPONSE_VALIDATION_CODE=" + code);
         }
      }
   }

   private static Round81ValidationException invalidArguments() {
      return failure("INVALID_ARGUMENTS");
   }

   private static Round81ValidationException failure(String code) {
      return new Round81ValidationException(code, "Controlled provider validation safety check failed");
   }

   private record ParsedArguments(Round81ProviderValidationCommand.ValidationMode mode, Round81ValidationOperation operation, boolean mock) {
   }

   private enum ValidationMode {
      DRY_RUN,
      VALIDATE;
   }

   private record VerifiedRuntime(Path environmentFile, Path jar, Path runDirectory, boolean mock) {
   }
}

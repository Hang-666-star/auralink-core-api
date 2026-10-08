package com.auralink.ops.round81;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.CreationProviderAdapter;
import com.auralink.creation.provider.ProviderAdapterBinding;
import com.auralink.creation.provider.ProviderAdapterRegistry;
import com.auralink.creation.provider.ProviderBinaryOutput;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderExecutionRequest;
import com.auralink.creation.provider.ProviderExecutionResult;
import com.auralink.creation.provider.ProviderImageInput;
import com.auralink.creation.provider.ProviderInput;
import com.auralink.creation.provider.ProviderReadiness;
import com.auralink.creation.provider.ProviderReadinessState;
import com.auralink.creation.provider.ProviderTextInput;
import com.auralink.provider.artifact.ProviderArtifact;
import com.auralink.provider.artifact.ProviderArtifactStagingService;
import com.auralink.provider.qwen.QwenResponseShapeDiagnostic;
import com.auralink.provider.qwen.QwenResponseValidationDiagnostic;
import com.auralink.workflow.WorkflowModality;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

final class Round81ProviderValidationCoordinator {
   private static final DateTimeFormatter FIXED_MILLIS_INSTANT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
   static final String TEXT_SOURCE = "春雨初歇，远山含黛，一叶归舟穿过薄雾，江岸疏林以水墨留白构成宁静的中国山水画面，无文字与标志。";
   static final String POEM_SOURCE = "空山新雨后，天气晚来秋。明月松间照，清泉石上流。";
   private final ProviderAdapterRegistry registry;
   private final ProviderArtifactStagingService stagingService;
   private final CreationProviderProperties properties;
   private final ObjectMapper objectMapper;
   private final Round81ProviderCallLedger ledger;
   private final Round81ResultRetainer resultRetainer;

   Round81ProviderValidationCoordinator(
      ProviderAdapterRegistry registry,
      ProviderArtifactStagingService stagingService,
      CreationProviderProperties properties,
      ObjectMapper objectMapper,
      Round81ProviderCallLedger ledger,
      Round81ResultRetainer resultRetainer
   ) {
      this.registry = registry;
      this.stagingService = stagingService;
      this.properties = properties;
      this.objectMapper = objectMapper;
      this.ledger = ledger;
      this.resultRetainer = resultRetainer;
   }

   void dryRun(Round81ValidationOperation operation) {
      this.requireRegistryContract(operation);
      this.requireReadiness(operation);
      this.requireStagingSafeWithoutMutation();
      if (!this.ledger.safeCounts().equals(Map.of("seedream", 0, "qwen", 0, "vmm", 0))) {
         throw new Round81ValidationException("DRY_RUN_CALL_COUNT_INVALID", "Dry run unexpectedly entered a provider transport");
      }
   }

   Round81RetainedResult validate(Round81ValidationOperation operation, Path runDirectory) {
      Path privateRun = Round81PrivateFiles.requirePrivateRunDirectory(runDirectory);
      this.requireRegistryContract(operation);
      this.requireReadiness(operation);
      this.requireExactConfirmation(operation);
      this.requireStagingEmpty();
      ProviderArtifact inputArtifact = null;
      ProviderExecutionResult result = null;
      Round81RetainedResult retainedResult = null;
      RuntimeException primaryFailure = null;
      long startedNanos = System.nanoTime();
      this.ledger.reset();

      try {
         ProviderInput input;
         if (operation == Round81ValidationOperation.TEXT_TO_PAINTING) {
            input = new ProviderTextInput("春雨初歇，远山含黛，一叶归舟穿过薄雾，江岸疏林以水墨留白构成宁静的中国山水画面，无文字与标志。", WorkflowModality.TEXT_DESCRIPTION);
         } else if (operation == Round81ValidationOperation.POEM_TO_PAINTING) {
            input = new ProviderTextInput("空山新雨后，天气晚来秋。明月松间照，清泉石上流。", WorkflowModality.POEM);
         } else {
            Round81InputManifest manifest = this.readInputManifest(privateRun);
            Path image = Round81PrivateFiles.requireContainedRegularFile(privateRun, manifest.inputFile());

            try (InputStream stream = Files.newInputStream(image)) {
               inputArtifact = this.stagingService.stageInputImage(stream, manifest.mimeType());
            }

            if (!manifest.sha256().equals(inputArtifact.sha256()) || manifest.width() != inputArtifact.width() || manifest.height() != inputArtifact.height()) {
               throw new Round81ValidationException("DETERMINISTIC_INPUT_MISMATCH", "Deterministic catalog input changed before execution");
            }

            input = new ProviderImageInput(
               inputArtifact, operation.inputModality(), operation.inputModality() == WorkflowModality.PAINTING ? manifest.paintingMetadata() : null
            );
         }

         String requestId = "round81_" + UUID.randomUUID().toString().replace("-", "");
         this.ledger.enterExecution();
         CreationProviderAdapter adapter = this.registry.require(operation.workflowOperation(), operation.providerCode());
         result = adapter.execute(new ProviderExecutionRequest(requestId, operation.workflowOperation(), operation.providerCode(), input));
         this.ledger.requireExact(operation);
         retainedResult = this.resultRetainer.retain(operation, result, privateRun);
         this.writeCallCounts(privateRun, operation);
         long elapsedMillis = Math.max(0L, (System.nanoTime() - startedNanos) / 1000000L);
         this.writeExecutionResult(privateRun, operation, requestId, retainedResult, elapsedMillis);
         return retainedResult;
      } catch (Round81ValidationException | ProviderExecutionException exception) {
         primaryFailure = exception;
         this.deleteIncompleteResult(privateRun, retainedResult);
         throw exception;
      } catch (Exception exception) {
         this.deleteIncompleteResult(privateRun, retainedResult);
         Round81ValidationException wrapped = new Round81ValidationException("VALIDATION_EXECUTION_FAILED", "Controlled provider validation failed", exception);
         primaryFailure = wrapped;
         throw wrapped;
      } finally {
         boolean cleanupComplete = false;

         try {
            this.cleanupArtifacts(result, inputArtifact, retainedResult, privateRun);
            cleanupComplete = true;
         } catch (RuntimeException cleanupFailure) {
            if (primaryFailure == null) {
               throw cleanupFailure;
            }

            primaryFailure.addSuppressed(cleanupFailure);
         } finally {
            if (primaryFailure != null) {
               this.writeSafeFailure(privateRun, operation, primaryFailure, cleanupComplete);
            }
         }
      }
   }

   private Round81InputManifest readInputManifest(Path runDirectory) {
      Path path = Round81PrivateFiles.requireContainedRegularFile(runDirectory, "input-metadata.json");
      return Round81InputManifest.read(this.objectMapper, path);
   }

   private void requireRegistryContract(Round81ValidationOperation operation) {
      CreationProviderAdapter adapter = this.registry.require(operation.workflowOperation(), operation.providerCode());
      long matches = adapter.bindings().stream().filter(binding -> this.matches(binding, operation)).count();
      if (matches != 1L) {
         throw new Round81ValidationException("PROVIDER_REGISTRY_MISMATCH", "Provider registry does not match the reviewed operation");
      }
   }

   private boolean matches(ProviderAdapterBinding binding, Round81ValidationOperation operation) {
      return binding.operation() == operation.workflowOperation()
         && binding.providerCode().equals(operation.providerCode())
         && binding.inputModality() == operation.inputModality()
         && binding.outputModality() == operation.outputModality();
   }

   private void requireReadiness(Round81ValidationOperation operation) {
      ProviderReadiness readiness = this.registry.readiness(operation.workflowOperation(), operation.providerCode());
      boolean accepted = readiness.state() == ProviderReadinessState.READY_FOR_CONTROLLED_EXECUTION
         || operation == Round81ValidationOperation.PAINTING_TO_MUSIC && readiness.state() == ProviderReadinessState.INTERNAL_SERVICE_NOT_VALIDATED;
      if (!accepted) {
         throw new Round81ValidationException("PROVIDER_NOT_READY", "Provider configuration is not ready for controlled validation");
      }
   }

   private void requireExactConfirmation(Round81ValidationOperation operation) {
      if (!operation.confirmation().equals(System.getenv("AURALINK_ROUND81_CONFIRM"))) {
         throw new Round81ValidationException("OPERATION_CONFIRMATION_REQUIRED", "The exact operation confirmation is required");
      }
   }

   private void requireStagingSafeWithoutMutation() {
      Path staging = this.properties.getStagingDir();
      if (staging != null && staging.isAbsolute()) {
         Path normalized = staging.toAbsolutePath().normalize();
         if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isSymbolicLink(normalized) && Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
               this.requireDirectoryEmpty(normalized);
            } else {
               throw new Round81ValidationException("STAGING_ROOT_INVALID", "Provider staging root is unsafe");
            }
         } else {
            Path parent = normalized.getParent();
            if (parent == null || Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(parent)) {
               throw new Round81ValidationException("STAGING_ROOT_INVALID", "Provider staging root cannot be created safely");
            }
         }
      } else {
         throw new Round81ValidationException("STAGING_ROOT_INVALID", "Provider staging root must be absolute");
      }
   }

   private void requireStagingEmpty() {
      Path staging = this.properties.getStagingDir().toAbsolutePath().normalize();
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
         if (!Files.isSymbolicLink(staging) && Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)) {
            this.requireDirectoryEmpty(staging);
         } else {
            throw new Round81ValidationException("STAGING_ROOT_INVALID", "Provider staging root is unsafe");
         }
      }
   }

   private void requireDirectoryEmpty(Path directory) {
      try (Stream<Path> entries = Files.list(directory)) {
         if (entries.findAny().isPresent()) {
            throw new Round81ValidationException("STAGING_ROOT_NOT_EMPTY", "Provider staging root is not empty");
         }
      } catch (Round81ValidationException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new Round81ValidationException("STAGING_ROOT_INVALID", "Provider staging root could not be inspected", exception);
      }
   }

   private void writeCallCounts(Path runDirectory, Round81ValidationOperation operation) {
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("operation", operation.workflowOperation().name());
      document.put("providerCode", operation.providerCode());
      document.put("executionEntered", true);
      document.put("calls", this.ledger.safeCounts());
      document.put("retryHandlerInvoked", false);
      document.put("outputCount", 1);
      Round81PrivateFiles.writeJson(this.objectMapper, runDirectory, "call-counts.json", document);
   }

   private void writeExecutionResult(
      Path runDirectory, Round81ValidationOperation operation, String requestId, Round81RetainedResult retained, long elapsedMillis
   ) {
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("status", "SUCCESS");
      document.put("requestId", requestId);
      document.put("operation", operation.workflowOperation().name());
      document.put("providerCode", operation.providerCode());
      document.put("outputModality", operation.outputModality().name());
      document.put("validatedAt", FIXED_MILLIS_INSTANT.format(Instant.now()));
      document.put("elapsedMillis", elapsedMillis);
      document.put("result", retained);
      Round81PrivateFiles.writeJson(this.objectMapper, runDirectory, "execution-result.json", document);
   }

   private void writeSafeFailure(Path runDirectory, Round81ValidationOperation operation, RuntimeException exception, boolean cleanupComplete) {
      String category = exception instanceof ProviderExecutionException provider
         ? provider.category().name()
         : (exception instanceof Round81ValidationException validation ? validation.code() : ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR.name());
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("status", "FAILED");
      document.put("safeErrorCategory", category);
      document.put("operation", operation.workflowOperation().name());
      document.put("providerCode", operation.providerCode());
      String providerFamily = this.ledger.safeLastProviderFamily();
      if (providerFamily != null) {
         document.put("providerFamily", providerFamily);
      }

      document.put("localCallCount", this.ledger.totalCallCount());
      document.put("calls", this.ledger.safeCounts());
      document.put("retryHandlerInvoked", false);
      if (exception instanceof ProviderExecutionException provider) {
         if (provider.providerHttpStatus() != null) {
            document.put("providerHttpStatus", provider.providerHttpStatus());
         }

         if (provider.providerErrorCode() != null) {
            document.put("providerErrorCode", provider.providerErrorCode());
         }

         if (provider.safeRequestId() != null) {
            document.put("safeRequestId", provider.safeRequestId());
         }

         if (provider.safeDiagnostic() instanceof QwenResponseValidationDiagnostic diagnostic) {
            document.put("validationStage", diagnostic.validationStage().name());
            document.put("validationCode", diagnostic.validationCode().name());
            Map<String, Object> responseShape = this.safeResponseShape(diagnostic.responseShape());
            if (!responseShape.isEmpty()) {
               document.put("responseShape", responseShape);
            }
         }
      }

      document.put("cleanupComplete", cleanupComplete);
      document.put("stagingEmpty", cleanupComplete);
      document.put("providerArtifactClosed", cleanupComplete);

      try {
         Round81PrivateFiles.writeJson(this.objectMapper, runDirectory, "failure.json", document);
      } catch (RuntimeException var11) {
      }
   }

   private Map<String, Object> safeResponseShape(QwenResponseShapeDiagnostic shape) {
      Map<String, Object> fields = new LinkedHashMap<>();
      this.put(fields, "providerEnvelopePresent", shape.providerEnvelopePresent());
      this.put(fields, "choicesPresent", shape.choicesPresent());
      this.put(fields, "choiceCount", shape.choiceCount());
      this.put(fields, "messagePresent", shape.messagePresent());
      this.put(fields, "reasoningContentPresent", shape.reasoningContentPresent());
      this.put(fields, "reasoningContentType", shape.reasoningContentType());
      this.put(fields, "reasoningContentNonblank", shape.reasoningContentNonblank());
      this.put(fields, "contentPresent", shape.contentPresent());
      this.put(fields, "contentType", shape.contentType());
      this.put(fields, "contentLength", shape.contentLength());
      this.put(fields, "jsonParsed", shape.jsonParsed());
      this.put(fields, "topLevelType", shape.topLevelType());
      this.put(fields, "schemaVersionPresent", shape.schemaVersionPresent());
      this.put(fields, "schemaVersionType", shape.schemaVersionType());
      this.put(fields, "titlePresent", shape.titlePresent());
      this.put(fields, "titleType", shape.titleType());
      this.put(fields, "titleLength", shape.titleLength());
      this.put(fields, "linesPresent", shape.linesPresent());
      this.put(fields, "linesType", shape.linesType());
      this.put(fields, "lineCount", shape.lineCount());
      this.put(fields, "stringLineCount", shape.stringLineCount());
      this.put(fields, "nonblankLineCount", shape.nonblankLineCount());
      this.put(fields, "chineseDominantLineCount", shape.chineseDominantLineCount());
      this.put(fields, "duplicateLineCount", shape.duplicateLineCount());
      this.put(fields, "minimumLineLength", shape.minimumLineLength());
      this.put(fields, "maximumLineLength", shape.maximumLineLength());
      this.put(fields, "textPresent", shape.textPresent());
      this.put(fields, "textType", shape.textType());
      this.put(fields, "textLength", shape.textLength());
      this.put(fields, "textMatchesLines", shape.textMatchesLines());
      this.put(fields, "unknownFieldCount", shape.unknownFieldCount());
      this.put(fields, "duplicateFieldCount", shape.duplicateFieldCount());
      this.put(fields, "hasLeadingOrTrailingContent", shape.hasLeadingOrTrailingContent());
      this.put(fields, "hasMarkdownFence", shape.hasMarkdownFence());
      this.put(fields, "hasHtml", shape.hasHtml());
      this.put(fields, "hasReasoningMarker", shape.hasReasoningMarker());
      this.put(fields, "hasAiSelfReference", shape.hasAiSelfReference());
      return fields;
   }

   private void put(Map<String, Object> fields, String name, Object value) {
      if (value instanceof Enum<?> token) {
         fields.put(name, token.name());
      } else if (value != null) {
         fields.put(name, value);
      }
   }

   private void writeCleanupResult(Path runDirectory) {
      Map<String, Object> document = Map.of("cleanupComplete", true, "stagingEmpty", true, "providerArtifactClosed", true);

      try {
         Round81PrivateFiles.writeJson(this.objectMapper, runDirectory, "cleanup-result.json", document);
      } catch (Round81ValidationException exception) {
         if (!"PRIVATE_RESULT_ALREADY_EXISTS".equals(exception.code())) {
            throw exception;
         }
      }
   }

   private void closeBinaryResult(ProviderExecutionResult result) {
      if (result != null && result.output() instanceof ProviderBinaryOutput binary) {
         binary.artifact().close();
      }
   }

   private void cleanupArtifacts(ProviderExecutionResult result, ProviderArtifact inputArtifact, Round81RetainedResult retainedResult, Path runDirectory) {
      RuntimeException cleanupFailure = null;

      try {
         this.closeBinaryResult(result);
      } catch (RuntimeException exception) {
         cleanupFailure = exception;
      }

      if (inputArtifact != null) {
         try {
            inputArtifact.close();
         } catch (RuntimeException exception) {
            if (cleanupFailure == null) {
               cleanupFailure = exception;
            } else {
               cleanupFailure.addSuppressed(exception);
            }
         }
      }

      try {
         this.requireStagingEmpty();
      } catch (RuntimeException exception) {
         if (cleanupFailure == null) {
            cleanupFailure = exception;
         } else {
            cleanupFailure.addSuppressed(exception);
         }
      }

      if (cleanupFailure != null) {
         try {
            this.deleteIncompleteResult(runDirectory, retainedResult);
         } catch (RuntimeException deletionFailure) {
            cleanupFailure.addSuppressed(deletionFailure);
         }

         throw cleanupFailure;
      } else {
         this.writeCleanupResult(runDirectory);
      }
   }

   private void deleteIncompleteResult(Path runDirectory, Round81RetainedResult retained) {
      if (retained != null) {
         Round81PrivateFiles.deleteDirectFile(runDirectory, retained.resultFile());
      }

      for (String evidence : new String[]{"result-metadata.json", "call-counts.json", "execution-result.json"}) {
         Round81PrivateFiles.deleteDirectFile(runDirectory, evidence);
      }
   }
}

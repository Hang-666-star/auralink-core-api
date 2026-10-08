package com.auralink.provider.seedream;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.CreationProviderAdapter;
import com.auralink.creation.provider.ProviderAdapterBinding;
import com.auralink.creation.provider.ProviderBinaryOutput;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderExecutionRequest;
import com.auralink.creation.provider.ProviderExecutionResult;
import com.auralink.creation.provider.ProviderImageInput;
import com.auralink.creation.provider.ProviderReadiness;
import com.auralink.creation.provider.ProviderTextInput;
import com.auralink.provider.artifact.ProviderArtifact;
import com.auralink.provider.validation.ProviderDataUrlEncoder;
import com.auralink.provider.validation.ProviderInputValidator;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "auralink.creation-providers", name = "mock-adapters-enabled", havingValue = "false", matchIfMissing = true)
public class SeedreamProviderAdapter implements CreationProviderAdapter {
   public static final String PROVIDER_CODE = "seedream-5";
   private static final List<ProviderAdapterBinding> BINDINGS = List.of(
      new ProviderAdapterBinding(WorkflowOperation.TEXT_TO_PAINTING, "seedream-5", WorkflowModality.TEXT_DESCRIPTION, WorkflowModality.PAINTING),
      new ProviderAdapterBinding(WorkflowOperation.IMAGE_TO_PAINTING, "seedream-5", WorkflowModality.IMAGE, WorkflowModality.PAINTING)
   );
   private final ProviderInputValidator inputValidator;
   private final ProviderDataUrlEncoder dataUrlEncoder;
   private final TextToPaintingPromptBuilder textPromptBuilder;
   private final ImageToPaintingPromptBuilder imagePromptBuilder;
   private final SeedreamImageGenerator generator;
   private final SeedreamEndpointPolicy endpointPolicy;
   private final CreationProviderProperties properties;

   @Override
   public List<ProviderAdapterBinding> bindings() {
      return BINDINGS;
   }

   @Override
   public ProviderReadiness readiness() {
      return this.endpointPolicy.readiness();
   }

   @Override
   public ProviderExecutionResult execute(ProviderExecutionRequest request) {
      this.requireBinding(request);
      ProviderArtifact artifact;
      if (request.operation() == WorkflowOperation.TEXT_TO_PAINTING) {
         if (!(request.input() instanceof ProviderTextInput textInput) || textInput.modality() != WorkflowModality.TEXT_DESCRIPTION) {
            throw this.rejected("TEXT_TO_PAINTING requires TEXT_DESCRIPTION input");
         }

         String source = this.inputValidator.validateText(textInput);
         artifact = this.generator.generate(request.requestId(), this.textPromptBuilder.build(source), null);
      } else {
         if (request.operation() != WorkflowOperation.IMAGE_TO_PAINTING) {
            throw this.rejected("Seedream operation is not supported");
         }

         if (!(request.input() instanceof ProviderImageInput imageInput) || imageInput.modality() != WorkflowModality.IMAGE) {
            throw this.rejected("IMAGE_TO_PAINTING requires IMAGE input");
         }

         ProviderArtifact source = this.inputValidator.validateImage(imageInput);
         String dataUrl = this.dataUrlEncoder.encodeImage(source, this.properties.getMaxImageInputBytes());
         artifact = this.generator.generate(request.requestId(), this.imagePromptBuilder.build(), dataUrl);
      }

      return new ProviderExecutionResult(request.requestId(), request.operation(), "seedream-5", WorkflowModality.PAINTING, new ProviderBinaryOutput(artifact));
   }

   private void requireBinding(ProviderExecutionRequest request) {
      if (request == null || !"seedream-5".equals(request.providerCode()) || BINDINGS.stream().noneMatch(binding -> binding.operation() == request.operation())
         )
       {
         throw this.rejected("Provider operation mapping is invalid");
      }
   }

   private ProviderExecutionException rejected(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_REJECTED, message);
   }

   public SeedreamProviderAdapter(
      final ProviderInputValidator inputValidator,
      final ProviderDataUrlEncoder dataUrlEncoder,
      final TextToPaintingPromptBuilder textPromptBuilder,
      final ImageToPaintingPromptBuilder imagePromptBuilder,
      final SeedreamImageGenerator generator,
      final SeedreamEndpointPolicy endpointPolicy,
      final CreationProviderProperties properties
   ) {
      this.inputValidator = inputValidator;
      this.dataUrlEncoder = dataUrlEncoder;
      this.textPromptBuilder = textPromptBuilder;
      this.imagePromptBuilder = imagePromptBuilder;
      this.generator = generator;
      this.endpointPolicy = endpointPolicy;
      this.properties = properties;
   }
}

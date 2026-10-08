package com.auralink.provider.qwen;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.CreationProviderAdapter;
import com.auralink.creation.provider.ProviderAdapterBinding;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderExecutionRequest;
import com.auralink.creation.provider.ProviderExecutionResult;
import com.auralink.creation.provider.ProviderImageInput;
import com.auralink.creation.provider.ProviderReadiness;
import com.auralink.creation.provider.ProviderTextOutput;
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
public class QwenPaintingToPoemProviderAdapter implements CreationProviderAdapter {
   public static final String PROVIDER_CODE = "qwen3-vl-plus";
   private static final ProviderAdapterBinding BINDING = new ProviderAdapterBinding(
      WorkflowOperation.PAINTING_TO_POEM, "qwen3-vl-plus", WorkflowModality.PAINTING, WorkflowModality.POEM
   );
   private final ProviderInputValidator inputValidator;
   private final ProviderDataUrlEncoder dataUrlEncoder;
   private final CreationProviderProperties properties;
   private final QwenCreationHttpClient client;
   private final QwenEndpointPolicy endpointPolicy;
   private final PaintingToPoemPromptBuilder promptBuilder;
   private final PaintingPoemResultValidator resultValidator;

   @Override
   public List<ProviderAdapterBinding> bindings() {
      return List.of(BINDING);
   }

   @Override
   public ProviderReadiness readiness() {
      return this.endpointPolicy.readiness();
   }

   @Override
   public ProviderExecutionResult execute(ProviderExecutionRequest request) {
      if (request != null
         && request.operation() == WorkflowOperation.PAINTING_TO_POEM
         && "qwen3-vl-plus".equals(request.providerCode())
         && request.input() instanceof ProviderImageInput imageInput
         && imageInput.modality() == WorkflowModality.PAINTING) {
         ProviderArtifact source = this.inputValidator.validateImage(imageInput);
         String dataUrl = this.dataUrlEncoder.encodeImage(source, this.properties.getMaxImageInputBytes());
         QwenResponseContent response = this.client
            .completeImageJsonWithShape(
               request.requestId(), this.promptBuilder.systemInstruction(), dataUrl, this.promptBuilder.userInstruction(imageInput.paintingMetadata())
            );
         PaintingPoemResult poem = this.resultValidator.validate(response);
         return new ProviderExecutionResult(
            request.requestId(),
            request.operation(),
            "qwen3-vl-plus",
            WorkflowModality.POEM,
            new ProviderTextOutput(poem.schemaVersion(), poem.title(), poem.lines(), poem.text())
         );
      } else {
         throw this.rejected("PAINTING_TO_POEM provider input is invalid");
      }
   }

   private ProviderExecutionException rejected(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_REJECTED, message);
   }

   public QwenPaintingToPoemProviderAdapter(
      final ProviderInputValidator inputValidator,
      final ProviderDataUrlEncoder dataUrlEncoder,
      final CreationProviderProperties properties,
      final QwenCreationHttpClient client,
      final QwenEndpointPolicy endpointPolicy,
      final PaintingToPoemPromptBuilder promptBuilder,
      final PaintingPoemResultValidator resultValidator
   ) {
      this.inputValidator = inputValidator;
      this.dataUrlEncoder = dataUrlEncoder;
      this.properties = properties;
      this.client = client;
      this.endpointPolicy = endpointPolicy;
      this.promptBuilder = promptBuilder;
      this.resultValidator = resultValidator;
   }
}

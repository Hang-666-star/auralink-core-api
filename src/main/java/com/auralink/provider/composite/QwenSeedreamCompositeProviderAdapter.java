package com.auralink.provider.composite;

import com.auralink.creation.provider.CreationProviderAdapter;
import com.auralink.creation.provider.ProviderAdapterBinding;
import com.auralink.creation.provider.ProviderBinaryOutput;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderExecutionRequest;
import com.auralink.creation.provider.ProviderExecutionResult;
import com.auralink.creation.provider.ProviderReadiness;
import com.auralink.creation.provider.ProviderReadinessState;
import com.auralink.creation.provider.ProviderTextInput;
import com.auralink.provider.artifact.ProviderArtifact;
import com.auralink.provider.qwen.PaintingPromptPlan;
import com.auralink.provider.qwen.QwenEndpointPolicy;
import com.auralink.provider.qwen.QwenPaintingPromptPlanner;
import com.auralink.provider.seedream.PoemPlanSeedreamPromptBuilder;
import com.auralink.provider.seedream.SeedreamEndpointPolicy;
import com.auralink.provider.seedream.SeedreamImageGenerator;
import com.auralink.provider.validation.ProviderInputValidator;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "auralink.creation-providers", name = "mock-adapters-enabled", havingValue = "false", matchIfMissing = true)
public class QwenSeedreamCompositeProviderAdapter implements CreationProviderAdapter {
   public static final String PROVIDER_CODE = "qwen3vl-seedream5";
   private static final ProviderAdapterBinding BINDING = new ProviderAdapterBinding(
      WorkflowOperation.POEM_TO_PAINTING, "qwen3vl-seedream5", WorkflowModality.POEM, WorkflowModality.PAINTING
   );
   private final ProviderInputValidator inputValidator;
   private final QwenPaintingPromptPlanner planner;
   private final PoemPlanSeedreamPromptBuilder promptBuilder;
   private final SeedreamImageGenerator seedreamGenerator;
   private final QwenEndpointPolicy qwenEndpointPolicy;
   private final SeedreamEndpointPolicy seedreamEndpointPolicy;

   @Override
   public List<ProviderAdapterBinding> bindings() {
      return List.of(BINDING);
   }

   @Override
   public ProviderReadiness readiness() {
      ProviderReadiness qwen = this.qwenEndpointPolicy.readiness();
      return qwen.state() != ProviderReadinessState.READY_FOR_CONTROLLED_EXECUTION ? qwen : this.seedreamEndpointPolicy.readiness();
   }

   @Override
   public ProviderExecutionResult execute(ProviderExecutionRequest request) {
      if (request != null
         && request.operation() == WorkflowOperation.POEM_TO_PAINTING
         && "qwen3vl-seedream5".equals(request.providerCode())
         && request.input() instanceof ProviderTextInput textInput
         && textInput.modality() == WorkflowModality.POEM) {
         String poem = this.inputValidator.validateText(textInput);
         this.seedreamGenerator.prepare();
         this.qwenEndpointPolicy.resolveChatCompletionsEndpoint();
         this.seedreamEndpointPolicy.resolveGenerationEndpoint();
         PaintingPromptPlan plan = this.planner.create(request.requestId(), poem);
         ProviderArtifact artifact = this.seedreamGenerator.generate(request.requestId(), this.promptBuilder.build(plan), null);
         return new ProviderExecutionResult(
            request.requestId(), request.operation(), "qwen3vl-seedream5", WorkflowModality.PAINTING, new ProviderBinaryOutput(artifact)
         );
      } else {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_REJECTED, "POEM_TO_PAINTING provider input is invalid");
      }
   }

   public QwenSeedreamCompositeProviderAdapter(
      final ProviderInputValidator inputValidator,
      final QwenPaintingPromptPlanner planner,
      final PoemPlanSeedreamPromptBuilder promptBuilder,
      final SeedreamImageGenerator seedreamGenerator,
      final QwenEndpointPolicy qwenEndpointPolicy,
      final SeedreamEndpointPolicy seedreamEndpointPolicy
   ) {
      this.inputValidator = inputValidator;
      this.planner = planner;
      this.promptBuilder = promptBuilder;
      this.seedreamGenerator = seedreamGenerator;
      this.qwenEndpointPolicy = qwenEndpointPolicy;
      this.seedreamEndpointPolicy = seedreamEndpointPolicy;
   }
}

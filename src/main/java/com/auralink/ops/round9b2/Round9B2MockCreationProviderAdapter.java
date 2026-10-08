package com.auralink.ops.round9b2;

import com.auralink.creation.provider.CreationProviderAdapter;
import com.auralink.creation.provider.PackagedMockCreationProviderAdapter;
import com.auralink.creation.provider.ProviderAdapterBinding;
import com.auralink.creation.provider.ProviderBinaryOutput;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderExecutionRequest;
import com.auralink.creation.provider.ProviderExecutionResult;
import com.auralink.creation.provider.ProviderReadiness;
import com.auralink.creation.provider.ProviderReadinessState;
import com.auralink.creation.provider.ProviderTextOutput;
import com.auralink.provider.artifact.ProviderArtifact;
import com.auralink.provider.artifact.ProviderArtifactStagingService;
import com.auralink.workflow.WorkflowModality;
import com.auralink.workflow.WorkflowOperation;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(prefix = "auralink.creation-providers", name = "mock-adapters-enabled", havingValue = "true")
public class Round9B2MockCreationProviderAdapter implements CreationProviderAdapter, PackagedMockCreationProviderAdapter {
   private static final byte[] PNG = png();
   private static final List<String> POEM_LINES = List.of("远岫含烟入暮云", "孤舟一叶过江津", "疏林淡墨留清韵", "月照寒波不染尘");
   private static final List<ProviderAdapterBinding> BINDINGS = List.of(
      new ProviderAdapterBinding(WorkflowOperation.TEXT_TO_PAINTING, "seedream-5", WorkflowModality.TEXT_DESCRIPTION, WorkflowModality.PAINTING),
      new ProviderAdapterBinding(WorkflowOperation.IMAGE_TO_PAINTING, "seedream-5", WorkflowModality.IMAGE, WorkflowModality.PAINTING),
      new ProviderAdapterBinding(WorkflowOperation.POEM_TO_PAINTING, "qwen3vl-seedream5", WorkflowModality.POEM, WorkflowModality.PAINTING),
      new ProviderAdapterBinding(WorkflowOperation.PAINTING_TO_POEM, "qwen3-vl-plus", WorkflowModality.PAINTING, WorkflowModality.POEM),
      new ProviderAdapterBinding(WorkflowOperation.PAINTING_TO_MUSIC, "auralink-vmm", WorkflowModality.PAINTING, WorkflowModality.AUDIO)
   );
   private final ProviderArtifactStagingService staging;
   private final AtomicInteger seedreamCalls = new AtomicInteger();
   private final AtomicInteger qwenCalls = new AtomicInteger();
   private final AtomicInteger vmmCalls = new AtomicInteger();
   private volatile boolean failNextCall;
   private volatile WorkflowOperation failNextOperation;
   private volatile boolean invalidNextOutput;

   public Round9B2MockCreationProviderAdapter(ProviderArtifactStagingService staging) {
      this.staging = staging;
   }

   @Override
   public List<ProviderAdapterBinding> bindings() {
      return BINDINGS;
   }

   @Override
   public ProviderReadiness readiness() {
      return new ProviderReadiness(ProviderReadinessState.READY_FOR_CONTROLLED_EXECUTION, "READY_FOR_CONTROLLED_EXECUTION");
   }

   @Override
   public ProviderExecutionResult execute(ProviderExecutionRequest request) {
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, "Mock adapter must not execute inside a transaction");
      }

      if (!this.failNextCall && request.operation() != this.failNextOperation) {
         return switch (request.operation()) {
            case TEXT_TO_PAINTING, IMAGE_TO_PAINTING -> this.painting(request, false);
            case POEM_TO_PAINTING -> this.painting(request, true);
            case PAINTING_TO_POEM -> this.poem(request);
            case PAINTING_TO_MUSIC -> this.music(request);
            case PAINTING_TO_VIDEO -> throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, "Unsupported mock operation");
         };
      } else {
         this.failNextCall = false;
         this.failNextOperation = null;
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_TIMEOUT, "Synthetic timeout");
      }
   }

   public void failNextCall() {
      this.failNextCall = true;
   }

   public void invalidateNextOutput() {
      this.invalidNextOutput = true;
   }

   public void failNextOperation(WorkflowOperation operation) {
      this.failNextOperation = operation;
   }

   public int seedreamCalls() {
      return this.seedreamCalls.get();
   }

   public int qwenCalls() {
      return this.qwenCalls.get();
   }

   public int vmmCalls() {
      return this.vmmCalls.get();
   }

   public static byte[] validPng() {
      return (byte[])PNG.clone();
   }

   private ProviderExecutionResult painting(ProviderExecutionRequest request, boolean composite) {
      if (composite) {
         this.qwenCalls.incrementAndGet();
      }

      this.seedreamCalls.incrementAndGet();
      ProviderArtifact artifact = this.staging.stageOutputImage(new ByteArrayInputStream(PNG), "image/png");
      if (this.invalidNextOutput) {
         this.invalidNextOutput = false;
         return new ProviderExecutionResult(
            request.requestId(),
            request.operation(),
            request.providerCode(),
            WorkflowModality.PAINTING,
            new ProviderBinaryOutput(artifact, "image/jpeg", artifact.byteLength(), artifact.sha256(), artifact.width(), artifact.height())
         );
      } else {
         return new ProviderExecutionResult(
            request.requestId(), request.operation(), request.providerCode(), WorkflowModality.PAINTING, new ProviderBinaryOutput(artifact)
         );
      }
   }

   private ProviderExecutionResult poem(ProviderExecutionRequest request) {
      this.qwenCalls.incrementAndGet();
      if (this.invalidNextOutput) {
         this.invalidNextOutput = false;
         return new ProviderExecutionResult(
            request.requestId(),
            request.operation(),
            request.providerCode(),
            WorkflowModality.POEM,
            new ProviderTextOutput("2", null, POEM_LINES, String.join("\n", POEM_LINES))
         );
      } else {
         return new ProviderExecutionResult(
            request.requestId(),
            request.operation(),
            request.providerCode(),
            WorkflowModality.POEM,
            new ProviderTextOutput("1", null, POEM_LINES, String.join("\n", POEM_LINES))
         );
      }
   }

   private ProviderExecutionResult music(ProviderExecutionRequest request) {
      int seconds = request.parameters().get("durationSeconds").intValue();
      if (seconds >= 3 && seconds <= 30) {
         this.vmmCalls.incrementAndGet();
         ProviderArtifact artifact = this.staging.stageOutputWave(new ByteArrayInputStream(wave(seconds)));
         return new ProviderExecutionResult(
            request.requestId(), request.operation(), request.providerCode(), WorkflowModality.AUDIO, new ProviderBinaryOutput(artifact)
         );
      } else {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, "Synthetic wave duration is invalid");
      }
   }

   private static byte[] png() {
      try {
         BufferedImage image = new BufferedImage(3, 2, 1);
         image.setRGB(0, 0, Color.BLACK.getRGB());
         image.setRGB(1, 0, Color.WHITE.getRGB());
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         if (!ImageIO.write(image, "PNG", output)) {
            throw new IllegalStateException("PNG encoder unavailable");
         } else {
            return output.toByteArray();
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Synthetic PNG could not be created", exception);
      }
   }

   private static byte[] wave(int seconds) {
      int sampleRate = 8000;
      int samples = sampleRate * seconds;
      int bytes = samples * 2;
      ByteBuffer output = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
      output.put("RIFF".getBytes(StandardCharsets.US_ASCII));
      output.putInt(36 + bytes);
      output.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
      output.putInt(16).putShort((short)1).putShort((short)1).putInt(sampleRate);
      output.putInt(sampleRate * 2).putShort((short)2).putShort((short)16);
      output.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);

      for (int index = 0; index < samples; index++) {
         output.putShort((short)(index % 80 < 40 ? 900 : -900));
      }

      return output.array();
   }
}

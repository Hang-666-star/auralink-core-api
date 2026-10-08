package com.auralink.creation.provider;

import com.auralink.provider.artifact.ProviderArtifact;

public record ProviderBinaryOutput(ProviderArtifact artifact, String mimeType, long byteLength, String sha256, Integer width, Integer height)
   implements ProviderOutput {
   public ProviderBinaryOutput(ProviderArtifact artifact) {
      this(artifact, artifact.mimeType(), artifact.byteLength(), artifact.sha256(), artifact.width(), artifact.height());
   }

   public ProviderBinaryOutput(ProviderArtifact artifact, String mimeType, long byteLength, String sha256, Integer width, Integer height) {
      if (artifact != null && mimeType != null && sha256 != null && byteLength >= 1L) {
         this.artifact = artifact;
         this.mimeType = mimeType;
         this.byteLength = byteLength;
         this.sha256 = sha256;
         this.width = width;
         this.height = height;
      } else {
         throw new IllegalArgumentException("Valid provider binary output is required");
      }
   }
}

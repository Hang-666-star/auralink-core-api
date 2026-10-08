package com.auralink.provider.seedream;

import com.auralink.provider.artifact.ProviderArtifact;

@FunctionalInterface
public interface SeedreamResultFetcher {
   default void prepare() {
   }

   ProviderArtifact fetch(String resultUrl);
}

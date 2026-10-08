package com.auralink.provider.seedream;

import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.provider.artifact.ProviderArtifact;
import com.auralink.provider.artifact.ProviderArtifactStagingService;
import com.auralink.service.SafeRemoteResourceFetcher;
import org.springframework.stereotype.Component;

@Component
public class SafeSeedreamResultFetcher implements SeedreamResultFetcher {
   private final SafeRemoteResourceFetcher remoteResourceFetcher;
   private final ProviderArtifactStagingService stagingService;

   @Override
   public void prepare() {
      this.stagingService.prepare();
   }

   @Override
   public ProviderArtifact fetch(String resultUrl) {
      try {
         return this.stagingService.stageOutputImage(target -> this.remoteResourceFetcher.fetchTo(resultUrl, target));
      } catch (ProviderExecutionException exception) {
         throw exception;
      } catch (RuntimeException exception) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Seedream image result was rejected", exception);
      }
   }

   public SafeSeedreamResultFetcher(final SafeRemoteResourceFetcher remoteResourceFetcher, final ProviderArtifactStagingService stagingService) {
      this.remoteResourceFetcher = remoteResourceFetcher;
      this.stagingService = stagingService;
   }
}

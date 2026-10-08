package com.auralink.provider.seedream;

import com.auralink.provider.ProviderBulkheadKind;
import com.auralink.provider.ProviderBulkheads;
import com.auralink.provider.artifact.ProviderArtifact;
import org.springframework.stereotype.Component;

@Component
public class SeedreamImageGenerator {
   private final SeedreamHttpClient httpClient;
   private final SeedreamResultFetcher resultFetcher;
   private final ProviderBulkheads bulkheads;

   public void prepare() {
      this.resultFetcher.prepare();
   }

   public ProviderArtifact generate(String requestId, String prompt, String imageDataUrl) {
      this.prepare();
      return this.bulkheads.execute(ProviderBulkheadKind.SEEDREAM, () -> {
         String resultUrl = this.httpClient.generate(requestId, prompt, imageDataUrl);
         return this.resultFetcher.fetch(resultUrl);
      });
   }

   public SeedreamImageGenerator(final SeedreamHttpClient httpClient, final SeedreamResultFetcher resultFetcher, final ProviderBulkheads bulkheads) {
      this.httpClient = httpClient;
      this.resultFetcher = resultFetcher;
      this.bulkheads = bulkheads;
   }
}

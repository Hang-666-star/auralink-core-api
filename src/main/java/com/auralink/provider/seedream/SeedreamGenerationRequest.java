package com.auralink.provider.seedream;

public sealed interface SeedreamGenerationRequest permits SeedreamTextGenerationRequest, SeedreamImageGenerationRequest {
   static SeedreamGenerationRequest text(String model, String prompt, String size, boolean watermark) {
      return new SeedreamTextGenerationRequest(model, prompt, "url", size, false, watermark);
   }

   static SeedreamGenerationRequest image(String model, String prompt, String image, String size, boolean watermark) {
      return new SeedreamImageGenerationRequest(model, prompt, image, "url", size, false, watermark);
   }
}

package com.auralink.provider.seedream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"model", "prompt", "image", "response_format", "size", "stream", "watermark"})
record SeedreamImageGenerationRequest(
   String model, String prompt, String image, @JsonProperty("response_format") String responseFormat, String size, boolean stream, boolean watermark
) implements SeedreamGenerationRequest {
}

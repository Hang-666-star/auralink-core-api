package com.auralink.provider.seedream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"model", "prompt", "response_format", "size", "stream", "watermark"})
record SeedreamTextGenerationRequest(
   String model, String prompt, @JsonProperty("response_format") String responseFormat, String size, boolean stream, boolean watermark
) implements SeedreamGenerationRequest {
}

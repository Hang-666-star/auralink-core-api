package com.auralink.provider.vmm;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"image", "duration"})
public record VmmGenerationRequest(String image, int duration) {
}

package com.auralink.guide.provider;

import com.auralink.guide.model.GuideResult;

public record GuideGenerationResult(String requestId, GuideResult result) {
}

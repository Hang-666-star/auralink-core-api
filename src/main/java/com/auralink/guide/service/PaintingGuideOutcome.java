package com.auralink.guide.service;

import com.auralink.guide.model.GuideResult;
import java.time.LocalDateTime;

public record PaintingGuideOutcome(String paintingId, GuideResult result, GuideCacheStatus cacheStatus, LocalDateTime generatedAt, LocalDateTime updatedAt) {
}

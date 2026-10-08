package com.auralink.catalogguide;

import com.auralink.guide.model.GuideResult;
import java.time.LocalDateTime;

public record HistoricalPaintingGuideOutcome(String paintingId, GuideResult result, LocalDateTime generatedAt, LocalDateTime updatedAt) {
}

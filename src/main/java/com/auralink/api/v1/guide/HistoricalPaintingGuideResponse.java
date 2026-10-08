package com.auralink.api.v1.guide;

import com.auralink.catalogguide.HistoricalPaintingGuideOutcome;
import com.auralink.guide.model.GuideKnowledgeReference;
import com.auralink.guide.model.GuideSections;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

public record HistoricalPaintingGuideResponse(
   String paintingId,
   String schemaVersion,
   String summary,
   GuideSections sections,
   List<String> highlights,
   List<GuideKnowledgeReference> knowledgeReferences,
   String resultScope,
   String inputProvenance,
   String currentApplicability,
   String generatedAt,
   String updatedAt
) {
   private static final ZoneId PERSISTED_TIMESTAMP_ZONE = ZoneId.of("Asia/Shanghai");
   private static final DateTimeFormatter PUBLIC_INSTANT_FORMATTER = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

   public static HistoricalPaintingGuideResponse from(HistoricalPaintingGuideOutcome outcome) {
      return new HistoricalPaintingGuideResponse(
         outcome.paintingId(),
         outcome.result().schemaVersion(),
         outcome.result().summary(),
         outcome.result().sections(),
         List.copyOf(outcome.result().highlights()),
         List.copyOf(outcome.result().knowledgeReferences()),
         "HISTORICAL_LEGACY",
         "LEGACY_PARTIAL",
         "UNVERIFIED",
         formatPersistedTimestamp(outcome.generatedAt()),
         formatPersistedTimestamp(outcome.updatedAt())
      );
   }

   private static String formatPersistedTimestamp(LocalDateTime timestamp) {
      return PUBLIC_INSTANT_FORMATTER.format(timestamp.atZone(PERSISTED_TIMESTAMP_ZONE).toInstant());
   }
}

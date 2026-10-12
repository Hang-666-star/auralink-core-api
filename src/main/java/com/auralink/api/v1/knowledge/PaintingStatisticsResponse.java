package com.auralink.api.v1.knowledge;

import java.util.List;
import java.util.Map;

/** Bounded public aggregates; no source rows, identifiers or private-field counts. */
public record PaintingStatisticsResponse(
   int schemaVersion, Population population, AuthorNames authorNames, Map<String, Metric> metrics
) {
   public record Population(String key, String label, long count) {}
   public record AuthorNames(long count, long eligibleRecords) {}
   public record Item(String name, double value) {}
   public record Metric(String status, String reason, long eligibleRecords, long missingOrRestrictedRecords,
                        long unmatchedRecords, List<Item> items, boolean truncated, String unit, String method) {
      public Metric { items = List.copyOf(items); }
   }
}

package com.auralink.api.v1.painting;

public record PaintingAnnotationResponse(
   String fieldKey,
   String label,
   String type,
   String unit,
   String group,
   int order,
   String visibility,
   String filterability,
   String originalColumn,
   String rawValue,
   String normalizedValue,
   String valueStatus,
   String assertionStatus
) {
}

package com.auralink.api.v1.critic;

import com.auralink.catalogcritic.CatalogCriticTask;
import com.auralink.entity.PaintingCritique;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record PaintingCritiqueTaskResponse(
   String critiqueId,
   String paintingId,
   String profile,
   String status,
   String legacyOriginalStatus,
   boolean reused,
   JsonNode result,
   String errorCode,
   String errorMessage,
   String createdAt,
   String startedAt,
   String finishedAt
) {
   public static PaintingCritiqueTaskResponse from(PaintingCritique task, boolean reused, ObjectMapper mapper) {
      return new PaintingCritiqueTaskResponse(
         task.getPublicId(),
         task.getPainting().getPublicId(),
         task.getProfile(),
         task.getStatus(),
         null,
         reused,
         parse(task.getResultJson(), mapper),
         task.getErrorCode(),
         task.getErrorMessage(),
         string(task.getCreatedAt()),
         string(task.getStartedAt()),
         string(task.getFinishedAt())
      );
   }

   public static PaintingCritiqueTaskResponse from(CatalogCriticTask task, boolean reused, ObjectMapper mapper) {
      return new PaintingCritiqueTaskResponse(
         task.publicId(),
         task.paintingId(),
         task.profile(),
         task.status(),
         task.legacyOriginalStatus(),
         reused,
         parse(task.resultJson(), mapper),
         task.errorCode(),
         task.errorMessage(),
         string(task.createdAt()),
         string(task.startedAt()),
         string(task.finishedAt())
      );
   }

   private static JsonNode parse(String value, ObjectMapper mapper) {
      try {
         return value == null ? null : mapper.readTree(value);
      } catch (Exception ignored) {
         return null;
      }
   }

   private static String string(Object value) {
      return value == null ? null : value.toString();
   }
}

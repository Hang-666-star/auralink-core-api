package com.auralink.api.v1.painting;

import java.util.List;

public record PaintingDetailResponse(
   String paintingId,
   String sourceSequence,
   String imageStorageName,
   String title,
   String authorName,
   String authorBirthYear,
   String authorBirthPlace,
   String authorSchool,
   String creationYear,
   String creationDynastyRaw,
   String creationDynastyNormalized,
   String actualSize,
   String collectionInstitution,
   String category,
   String subject,
   String paintingSchool,
   String style,
   String color,
   String composition,
   String artisticConception,
   String brushwork,
   String inkMethod,
   String paintingMaterial,
   String pigment,
   String seal,
   String culturalSymbol,
   String generatedText,
   String musicSceneDescription,
   String collectionPlatform,
   boolean imageAvailable,
   boolean visibleInGallery,
   String status,
   PaintingImageResponse image,
   boolean favorited,
   List<PaintingAnnotationResponse> annotations
) {
   public PaintingDetailResponse {
      annotations = List.copyOf(annotations);
   }
}

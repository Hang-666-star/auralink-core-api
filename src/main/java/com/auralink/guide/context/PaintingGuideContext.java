package com.auralink.guide.context;

import com.auralink.guide.knowledge.KnowledgeItem;
import java.util.List;

public record PaintingGuideContext(
   String paintingId,
   PaintingGuideContext.Basic basic,
   PaintingGuideContext.Artist artist,
   PaintingGuideContext.Art art,
   PaintingGuideContext.OfficialAnnotations officialAnnotations,
   List<KnowledgeItem> knowledge
) {
   public PaintingGuideContext {
      knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
   }

   public PaintingGuideContext withKnowledge(List<KnowledgeItem> selectedKnowledge) {
      return new PaintingGuideContext(this.paintingId, this.basic, this.artist, this.art, this.officialAnnotations, selectedKnowledge);
   }

   public record Art(
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
      String culturalSymbol
   ) {
   }

   public record Artist(String name, String birthYear, String birthPlace, String school) {
   }

   public record Basic(
      String title, String creationYear, String creationDynastyRaw, String creationDynastyNormalized, String actualSize, String collectionInstitution
   ) {
   }

   public record OfficialAnnotations(String generatedText, String musicSceneDescription) {
   }
}

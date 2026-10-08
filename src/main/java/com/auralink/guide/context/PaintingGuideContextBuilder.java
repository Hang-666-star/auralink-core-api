package com.auralink.guide.context;

import com.auralink.catalogread.CatalogReadStore;
import com.auralink.entity.Painting;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaintingGuideContextBuilder {
   public PaintingGuideContext build(Painting painting) {
      if (painting == null) {
         throw new IllegalArgumentException("Painting is required");
      } else {
         return new PaintingGuideContext(
            this.clean(painting.getPublicId()),
            new PaintingGuideContext.Basic(
               this.clean(painting.getTitle()),
               this.clean(painting.getCreationYear()),
               this.clean(painting.getCreationDynastyRaw()),
               this.clean(painting.getCreationDynastyNormalized()),
               this.clean(painting.getActualSize()),
               this.clean(painting.getCollectionInstitution())
            ),
            new PaintingGuideContext.Artist(
               this.clean(painting.getAuthorName()),
               this.clean(painting.getAuthorBirthYear()),
               this.clean(painting.getAuthorBirthPlace()),
               this.clean(painting.getAuthorSchool())
            ),
            new PaintingGuideContext.Art(
               this.clean(painting.getCategory()),
               this.clean(painting.getSubject()),
               this.clean(painting.getPaintingSchool()),
               this.clean(painting.getStyle()),
               this.clean(painting.getColor()),
               this.clean(painting.getComposition()),
               this.clean(painting.getArtisticConception()),
               this.clean(painting.getBrushwork()),
               this.clean(painting.getInkMethod()),
               this.clean(painting.getPaintingMaterial()),
               this.clean(painting.getPigment()),
               this.clean(painting.getSeal()),
               this.clean(painting.getCulturalSymbol())
            ),
            new PaintingGuideContext.OfficialAnnotations(this.clean(painting.getGeneratedText()), this.clean(painting.getMusicSceneDescription())),
            List.of()
         );
      }
   }

   public PaintingGuideContext build(CatalogReadStore.CatalogPainting painting) {
      if (painting == null) {
         throw new IllegalArgumentException("Painting is required");
      } else {
         return new PaintingGuideContext(
            this.clean(painting.publicId()),
            new PaintingGuideContext.Basic(
               this.clean(painting.title()),
               this.clean(painting.value("legacy.creation_year")),
               this.clean(painting.value("legacy.creation_dynasty_raw")),
               this.clean(painting.value("legacy.creation_dynasty_normalized")),
               this.clean(painting.value("legacy.actual_size")),
               this.clean(painting.value("legacy.collection_institution"))
            ),
            new PaintingGuideContext.Artist(
               this.clean(painting.authorName()),
               this.clean(painting.value("legacy.author_birth_year")),
               this.clean(painting.value("legacy.author_birth_place")),
               this.clean(painting.value("legacy.author_school"))
            ),
            new PaintingGuideContext.Art(
               this.clean(painting.category()),
               this.clean(painting.value("legacy.subject")),
               this.clean(painting.value("legacy.painting_school")),
               this.clean(painting.value("legacy.style")),
               this.clean(painting.value("legacy.color")),
               this.clean(painting.value("legacy.composition")),
               this.clean(painting.value("legacy.artistic_conception")),
               this.clean(painting.value("legacy.brushwork")),
               this.clean(painting.value("legacy.ink_method")),
               this.clean(painting.value("legacy.painting_material")),
               this.clean(painting.value("legacy.pigment")),
               this.clean(painting.value("legacy.seal")),
               this.clean(painting.value("legacy.cultural_symbol"))
            ),
            new PaintingGuideContext.OfficialAnnotations(
               this.clean(painting.value("legacy.generated_text")), this.clean(painting.value("legacy.music_scene_description"))
            ),
            List.of()
         );
      }
   }

   private String clean(String value) {
      if (value == null) {
         return null;
      }

      String cleaned = value.strip();
      return !cleaned.isEmpty() && !"0".equals(cleaned) ? cleaned : null;
   }
}

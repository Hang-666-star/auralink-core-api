package com.auralink.ops.round81;

import com.auralink.creation.provider.PaintingMetadataContext;
import com.auralink.provider.validation.StrictProviderJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

record Round81InputManifest(
   String paintingId,
   String title,
   String author,
   String dynasty,
   String category,
   String subject,
   String paintingSchool,
   String style,
   String composition,
   String artisticConception,
   String generatedText,
   String musicSceneDescription,
   String mimeType,
   int width,
   int height,
   String sha256,
   String inputFile
) {
   private static final Set<String> FIELDS = Set.of(
      "paintingId",
      "title",
      "author",
      "dynasty",
      "category",
      "subject",
      "paintingSchool",
      "style",
      "composition",
      "artisticConception",
      "generatedText",
      "musicSceneDescription",
      "mimeType",
      "width",
      "height",
      "sha256",
      "inputFile"
   );

   static Round81InputManifest read(ObjectMapper mapper, Path path) {
      JsonNode root;
      try {
         root = StrictProviderJson.parse(mapper, Files.readAllBytes(path));
      } catch (Exception exception) {
         throw new Round81ValidationException("INPUT_MANIFEST_INVALID", "Deterministic input metadata is invalid", exception);
      }

      if (root != null && root.isObject()) {
         Set<String> names = new HashSet<>();
         Iterator<String> iterator = root.fieldNames();
         iterator.forEachRemaining(names::add);
         if (!names.equals(FIELDS)) {
            throw invalid();
         }

         try {
            Round81InputManifest manifest = (Round81InputManifest)mapper.treeToValue(root, Round81InputManifest.class);
            manifest.validate();
            return manifest;
         } catch (Round81ValidationException exception) {
            throw exception;
         } catch (Exception exception) {
            throw new Round81ValidationException("INPUT_MANIFEST_INVALID", "Deterministic input metadata is invalid", exception);
         }
      } else {
         throw invalid();
      }
   }

   PaintingMetadataContext paintingMetadata() {
      return new PaintingMetadataContext(
         this.paintingId,
         this.title,
         this.author,
         this.dynasty,
         this.category,
         this.subject,
         this.paintingSchool,
         this.style,
         this.composition,
         this.artisticConception,
         this.generatedText,
         this.musicSceneDescription
      );
   }

   private void validate() {
      try {
         UUID.fromString(this.paintingId);
      } catch (RuntimeException exception) {
         throw invalid();
      }

      if (("image/jpeg".equals(this.mimeType) || "image/png".equals(this.mimeType))
         && this.width >= 1
         && this.height >= 1
         && this.sha256 != null
         && this.sha256.matches("[0-9a-f]{64}")
         && this.inputFile != null
         && this.inputFile.matches("input-image\\.(?:jpg|png)")) {
         for (String value : new String[]{
            this.title,
            this.author,
            this.dynasty,
            this.category,
            this.subject,
            this.paintingSchool,
            this.style,
            this.composition,
            this.artisticConception,
            this.generatedText,
            this.musicSceneDescription
         }) {
            if (value != null && (value.length() > 8000 || value.chars().anyMatch(character -> character == 0))) {
               throw invalid();
            }
         }
      } else {
         throw invalid();
      }
   }

   private static Round81ValidationException invalid() {
      return new Round81ValidationException("INPUT_MANIFEST_INVALID", "Deterministic input metadata is invalid");
   }
}

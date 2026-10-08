package com.auralink.guide.model;

import com.auralink.guide.knowledge.KnowledgeItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GuideResultCodec {
   public static final int MAX_RESULT_BYTES = 65536;
   public static final int MAX_SUMMARY_CHARACTERS = 2000;
   public static final int MAX_SECTION_CHARACTERS = 4000;
   public static final int MAX_HIGHLIGHT_CHARACTERS = 500;
   public static final int MAX_REFERENCE_COUNT = 5;
   private static final int MIN_HIGHLIGHT_COUNT = 2;
   private static final int MAX_HIGHLIGHT_COUNT = 5;
   private static final int MAX_SOURCE_ID_CHARACTERS = 256;
   private static final int MAX_SOURCE_TYPE_CHARACTERS = 64;
   private static final int MAX_REFERENCE_TITLE_CHARACTERS = 512;
   private static final Pattern HTML_TAG = Pattern.compile("(?is)<\\s*/?\\s*[a-z][^>]*>");
   private static final Pattern HAN_CHARACTER = Pattern.compile("\\p{IsHan}");
   private final ObjectReader strictReader;
   private final ObjectWriter canonicalWriter;

   public GuideResultCodec(ObjectMapper objectMapper) {
      Objects.requireNonNull(objectMapper, "objectMapper");
      ObjectMapper strictMapper = objectMapper.copy()
         .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
         .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
         .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
         .disable(SerializationFeature.INDENT_OUTPUT);
      this.strictReader = strictMapper.readerFor(GuideResult.class);
      this.canonicalWriter = strictMapper.writerFor(GuideResult.class);
   }

   public GuideResult decode(String json, String expectedSchemaVersion, List<KnowledgeItem> allowedKnowledge) {
      if (json != null && !json.isBlank()) {
         this.requireByteLimit(json, "Guide result JSON");

         try {
            return this.validate((GuideResult)this.strictReader.readValue(json), expectedSchemaVersion, allowedKnowledge);
         } catch (GuideResultValidationException exception) {
            throw exception;
         } catch (JsonProcessingException exception) {
            throw new GuideResultValidationException("Guide result JSON is malformed", exception);
         }
      } else {
         throw this.invalid("Guide result JSON is absent");
      }
   }

   public GuideResult decodeHistorical(String json, String expectedSchemaVersion) {
      if (json != null && !json.isBlank()) {
         this.requireByteLimit(json, "Historical Guide result JSON");

         try {
            return this.validateHistorical((GuideResult)this.strictReader.readValue(json), expectedSchemaVersion);
         } catch (GuideResultValidationException exception) {
            throw exception;
         } catch (JsonProcessingException exception) {
            throw new GuideResultValidationException("Historical Guide result JSON is malformed", exception);
         }
      } else {
         throw this.invalid("Historical Guide result JSON is absent");
      }
   }

   public String encodeCanonical(GuideResult result, String expectedSchemaVersion, List<KnowledgeItem> allowedKnowledge) {
      GuideResult normalized = this.validate(result, expectedSchemaVersion, allowedKnowledge);

      try {
         String json = this.canonicalWriter.writeValueAsString(normalized);
         this.requireByteLimit(json, "Canonical Guide result");
         return json;
      } catch (JsonProcessingException exception) {
         throw new GuideResultValidationException("Guide result cannot be serialized", exception);
      }
   }

   public GuideResult validate(GuideResult result, String expectedSchemaVersion, List<KnowledgeItem> allowedKnowledge) {
      if (result == null) {
         throw this.invalid("Guide result is absent");
      }

      String schemaVersion = this.requireText(result.schemaVersion(), "schemaVersion", 32, false);
      String expected = this.requireText(expectedSchemaVersion, "expected schemaVersion", 32, false);
      if (!expected.equals(schemaVersion)) {
         throw this.invalid("Guide schemaVersion does not match the requested schema");
      }

      String summary = this.requireChineseText(result.summary(), "summary", 2000, true);
      GuideSections sections = this.normalizeSections(result.sections());
      List<String> highlights = this.normalizeHighlights(result.highlights());
      List<GuideKnowledgeReference> references = this.normalizeReferences(result.knowledgeReferences(), allowedKnowledge, true);
      return this.normalizedResult(schemaVersion, summary, sections, highlights, references);
   }

   private GuideResult validateHistorical(GuideResult result, String expectedSchemaVersion) {
      if (result == null) {
         throw this.invalid("Historical Guide result is absent");
      }

      String schemaVersion = this.requireText(result.schemaVersion(), "schemaVersion", 32, false);
      String expected = this.requireText(expectedSchemaVersion, "expected schemaVersion", 32, false);
      if (!expected.equals(schemaVersion)) {
         throw this.invalid("Guide schemaVersion does not match the requested schema");
      }

      String summary = this.requireChineseText(result.summary(), "summary", 2000, true);
      GuideSections sections = this.normalizeSections(result.sections());
      List<String> highlights = this.normalizeHighlights(result.highlights());
      List<GuideKnowledgeReference> references = this.normalizeReferences(result.knowledgeReferences(), List.of(), false);
      return this.normalizedResult(schemaVersion, summary, sections, highlights, references);
   }

   private GuideResult normalizedResult(
      String schemaVersion, String summary, GuideSections sections, List<String> highlights, List<GuideKnowledgeReference> references
   ) {
      GuideResult normalized = new GuideResult(schemaVersion, summary, sections, List.copyOf(highlights), List.copyOf(references));

      try {
         this.requireByteLimit(this.canonicalWriter.writeValueAsString(normalized), "Guide result");
         return normalized;
      } catch (JsonProcessingException exception) {
         throw new GuideResultValidationException("Guide result cannot be serialized", exception);
      }
   }

   private GuideSections normalizeSections(GuideSections sections) {
      if (sections == null) {
         throw this.invalid("sections is required");
      } else {
         return new GuideSections(
            this.optionalChineseText(sections.artistAndEra(), "sections.artistAndEra", 4000),
            this.optionalChineseText(sections.subjectAndScene(), "sections.subjectAndScene", 4000),
            this.optionalChineseText(sections.composition(), "sections.composition", 4000),
            this.optionalChineseText(sections.brushworkAndInk(), "sections.brushworkAndInk", 4000),
            this.optionalChineseText(sections.colorAndMaterial(), "sections.colorAndMaterial", 4000),
            this.optionalChineseText(sections.artisticConception(), "sections.artisticConception", 4000),
            this.optionalChineseText(sections.culturalMeaning(), "sections.culturalMeaning", 4000),
            this.optionalChineseText(sections.musicAssociation(), "sections.musicAssociation", 4000)
         );
      }
   }

   private List<String> normalizeHighlights(List<String> highlights) {
      if (highlights != null && highlights.size() >= 2 && highlights.size() <= 5) {
         List<String> normalized = new ArrayList<>(highlights.size());
         Set<String> unique = new HashSet<>();

         for (String highlight : highlights) {
            String safe = this.requireChineseText(highlight, "highlights item", 500, true);
            if (!unique.add(safe)) {
               throw this.invalid("highlights must not contain duplicates");
            }

            normalized.add(safe);
         }

         return normalized;
      } else {
         throw this.invalid("highlights must contain between 2 and 5 items");
      }
   }

   private List<GuideKnowledgeReference> normalizeReferences(
      List<GuideKnowledgeReference> references, List<KnowledgeItem> allowedKnowledge, boolean requireCurrentMembership
   ) {
      List<GuideKnowledgeReference> supplied = references == null ? List.of() : references;
      if (supplied.size() > 5) {
         throw this.invalid("knowledgeReferences exceeds the allowed item count");
      }

      Map<String, KnowledgeItem> allowedById = new HashMap<>();

      for (KnowledgeItem item : allowedKnowledge == null ? List.<KnowledgeItem>of() : allowedKnowledge) {
         if (item != null && item.sourceId() != null && !item.sourceId().isBlank()) {
            KnowledgeItem duplicate = allowedById.putIfAbsent(item.sourceId(), item);
            if (duplicate != null) {
               throw this.invalid("Supplied knowledge contains a duplicate sourceId");
            }
         }
      }

      List<GuideKnowledgeReference> normalized = new ArrayList<>(supplied.size());
      Set<String> referencedIds = new HashSet<>();

      for (GuideKnowledgeReference reference : supplied) {
         if (reference == null) {
            throw this.invalid("knowledgeReferences contains a null item");
         }

         String sourceId = this.requireText(reference.sourceId(), "knowledgeReferences.sourceId", 256, false);
         String sourceType = this.requireText(reference.sourceType(), "knowledgeReferences.sourceType", 64, false);
         String title = this.requireText(reference.title(), "knowledgeReferences.title", 512, true);
         if (!referencedIds.add(sourceId)) {
            throw this.invalid("knowledgeReferences must not repeat a sourceId");
         }

         KnowledgeItem allowed = allowedById.get(sourceId);
         if (requireCurrentMembership && (allowed == null || !sourceType.equals(allowed.sourceType()) || !title.equals(allowed.title()))) {
            throw this.invalid("knowledgeReferences contains an unsupported reference");
         }

         normalized.add(new GuideKnowledgeReference(sourceId, sourceType, title));
      }

      return normalized;
   }

   private String optionalChineseText(String value, String field, int maxCharacters) {
      return value != null && !value.isBlank() ? this.requireChineseText(value, field, maxCharacters, true) : null;
   }

   private String requireChineseText(String value, String field, int maxCharacters, boolean rejectMarkup) {
      String normalized = this.requireText(value, field, maxCharacters, rejectMarkup);
      if (!HAN_CHARACTER.matcher(normalized).find()) {
         throw this.invalid(field + " must contain Chinese text");
      } else {
         return normalized;
      }
   }

   private String requireText(String value, String field, int maxCharacters, boolean rejectMarkup) {
      if (value != null && !value.isBlank()) {
         String normalized = value.trim();
         if (normalized.length() > maxCharacters) {
            throw this.invalid(field + " exceeds its character limit");
         }

         if (rejectMarkup) {
            this.rejectMarkup(normalized, field);
         }

         return normalized;
      } else {
         throw this.invalid(field + " is required");
      }
   }

   private void rejectMarkup(String value, String field) {
      if (value.contains("```") || HTML_TAG.matcher(value).find()) {
         throw this.invalid(field + " must not contain code fences or HTML");
      }
   }

   private void requireByteLimit(String value, String field) {
      if (value.getBytes(StandardCharsets.UTF_8).length > 65536) {
         throw this.invalid(field + " exceeds the byte limit");
      }
   }

   private GuideResultValidationException invalid(String message) {
      return new GuideResultValidationException(message);
   }
}

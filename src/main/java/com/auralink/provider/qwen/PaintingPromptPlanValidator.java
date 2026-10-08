package com.auralink.provider.qwen;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.provider.validation.ChineseTextRules;
import com.auralink.provider.validation.StrictProviderJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class PaintingPromptPlanValidator {
   private static final Set<String> FIELDS = Set.of(
      "schemaVersion", "subject", "scene", "composition", "colorPalette", "brushwork", "artisticConception", "finalPrompt"
   );
   private static final int MAX_FIELD_CHARS = 4000;
   private final ObjectMapper objectMapper;
   private final CreationProviderProperties properties;

   public PaintingPromptPlan validate(String rawJson) {
      JsonNode root = this.parseObject(rawJson, "Qwen Painting prompt plan is not valid JSON");
      this.requireExactFields(root);
      String schemaVersion = this.text(root, "schemaVersion");
      if (!"1".equals(schemaVersion)) {
         throw this.invalid("Qwen Painting prompt plan schema is unsupported");
      } else {
         String subject = this.validatedChinese(root, "subject");
         String scene = this.validatedChinese(root, "scene");
         String composition = this.validatedChinese(root, "composition");
         String colorPalette = this.validatedChinese(root, "colorPalette");
         String brushwork = this.validatedChinese(root, "brushwork");
         String artisticConception = this.validatedChinese(root, "artisticConception");
         String finalPrompt = this.validatedChinese(root, "finalPrompt");
         int total = subject.length()
            + scene.length()
            + composition.length()
            + colorPalette.length()
            + brushwork.length()
            + artisticConception.length()
            + finalPrompt.length();
         if (total > this.properties.getMaxTextChars()) {
            throw this.invalid("Qwen Painting prompt plan exceeds the configured character limit");
         } else if (Stream.of(subject, scene, composition, colorPalette, brushwork, artisticConception, finalPrompt)
            .anyMatch(ChineseTextRules::containsUnsupportedAuthorshipClaim)) {
            throw this.invalid("Qwen Painting prompt plan contains an unsupported authorship claim");
         } else {
            return new PaintingPromptPlan(schemaVersion, subject, scene, composition, colorPalette, brushwork, artisticConception, finalPrompt);
         }
      }
   }

   private JsonNode parseObject(String rawJson, String message) {
      if (rawJson != null && !rawJson.isBlank() && !rawJson.contains("```")) {
         try {
            JsonNode root = StrictProviderJson.parse(this.objectMapper, rawJson);
            if (root != null && root.isObject()) {
               return root;
            } else {
               throw this.invalid(message);
            }
         } catch (ProviderExecutionException exception) {
            throw exception;
         } catch (Exception exception) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INVALID_RESPONSE, message, exception);
         }
      } else {
         throw this.invalid(message);
      }
   }

   private void requireExactFields(JsonNode root) {
      Set<String> actual = new HashSet<>();
      Iterator<String> fields = root.fieldNames();
      fields.forEachRemaining(actual::add);
      if (!actual.equals(FIELDS)) {
         throw this.invalid("Qwen Painting prompt plan fields are invalid");
      }
   }

   private String validatedChinese(JsonNode root, String field) {
      String value = this.text(root, field);
      if (value.length() <= 4000 && ChineseTextRules.containsChinese(value) && !ChineseTextRules.containsForbiddenMarkupOrLeakage(value)) {
         return value;
      } else {
         throw this.invalid("Qwen Painting prompt plan content is invalid");
      }
   }

   private String text(JsonNode root, String field) {
      JsonNode value = root.get(field);
      if (value != null && value.isTextual() && !value.textValue().trim().isEmpty()) {
         return value.textValue().trim();
      } else {
         throw this.invalid("Qwen Painting prompt plan field is missing");
      }
   }

   private ProviderExecutionException invalid(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INVALID_RESPONSE, message);
   }

   public PaintingPromptPlanValidator(final ObjectMapper objectMapper, final CreationProviderProperties properties) {
      this.objectMapper = objectMapper;
      this.properties = properties;
   }
}

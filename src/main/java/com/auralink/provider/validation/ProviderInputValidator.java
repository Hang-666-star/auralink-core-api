package com.auralink.provider.validation;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.PaintingMetadataContext;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderImageInput;
import com.auralink.creation.provider.ProviderTextInput;
import com.auralink.provider.artifact.ProviderArtifact;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProviderInputValidator {
   private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
   private final CreationProviderProperties properties;

   public String validateText(ProviderTextInput input) {
      if (input == null) {
         throw this.invalid("Provider text input is required");
      }

      String text = input.text().trim();
      if (text.isEmpty()) {
         throw this.invalid("Provider text input must not be blank");
      }

      if (text.length() > this.properties.getMaxTextChars()) {
         throw this.invalid("Provider text input exceeds the configured character limit");
      }

      this.requireSafeText(text, "Provider text input contains unsupported control characters");
      return text;
   }

   public ProviderArtifact validateImage(ProviderImageInput input) {
      if (input != null && input.artifact() != null) {
         ProviderArtifact artifact = input.artifact();
         if (artifact.isAvailable()
            && artifact.byteLength() >= 1L
            && artifact.byteLength() <= this.properties.getMaxImageInputBytes()
            && ("image/jpeg".equals(artifact.mimeType()) || "image/png".equals(artifact.mimeType()))
            && artifact.width() != null
            && artifact.width() >= 1
            && artifact.height() != null
            && artifact.height() >= 1
            && artifact.sha256() != null
            && SHA256.matcher(artifact.sha256()).matches()) {
            this.validateMetadata(input.paintingMetadata());
            return artifact;
         } else {
            throw this.invalid("Provider image input failed validation");
         }
      } else {
         throw this.invalid("Provider image input is required");
      }
   }

   public void validateMetadata(PaintingMetadataContext metadata) {
      if (metadata != null) {
         this.requireOptionalPaintingId(metadata.paintingId());
         long total = 0L;
         total += this.requireOptionalSafe(metadata.title());
         total += this.requireOptionalSafe(metadata.author());
         total += this.requireOptionalSafe(metadata.dynasty());
         total += this.requireOptionalSafe(metadata.category());
         total += this.requireOptionalSafe(metadata.subject());
         total += this.requireOptionalSafe(metadata.paintingSchool());
         total += this.requireOptionalSafe(metadata.style());
         total += this.requireOptionalSafe(metadata.composition());
         total += this.requireOptionalSafe(metadata.artisticConception());
         total += this.requireOptionalSafe(metadata.generatedText());
         total += this.requireOptionalSafe(metadata.musicSceneDescription());
         if (total > this.properties.getMaxTextChars()) {
            throw this.invalid("Painting metadata exceeds the configured total character limit");
         }
      }
   }

   public void requireSafeText(String value, String message) {
      if (value == null) {
         throw this.invalid(message);
      }

      for (int index = 0; index < value.length(); index++) {
         char character = value.charAt(index);
         if (character == 0 || Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t') {
            throw this.invalid(message);
         }
      }
   }

   private int requireOptionalSafe(String value) {
      if (value == null) {
         return 0;
      }

      if (value.length() > this.properties.getMaxTextChars()) {
         throw this.invalid("Painting metadata exceeds the configured character limit");
      }

      this.requireSafeText(value, "Painting metadata contains unsupported control characters");
      return value.length();
   }

   private void requireOptionalPaintingId(String value) {
      if (value != null) {
         this.requireSafeText(value, "Painting identifier contains unsupported control characters");

         try {
            if (!UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT))) {
               throw this.invalid("Painting identifier is not a public UUID");
            }
         } catch (IllegalArgumentException exception) {
            throw this.invalid("Painting identifier is not a public UUID");
         }
      }
   }

   private ProviderExecutionException invalid(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_REJECTED, message);
   }

   public ProviderInputValidator(final CreationProviderProperties properties) {
      this.properties = properties;
   }
}

package com.auralink.media;

import java.util.Locale;
import java.util.Set;

public final class MediaAssetValues {
   private static final Set<String> ASSET_TYPES = Set.of("IMAGE", "AUDIO", "VIDEO", "FILE");
   private static final Set<String> SEMANTIC_TYPES = Set.of("IMAGE", "PAINTING", "GENERATED_PAINTING", "MUSIC", "VIDEO", "OTHER");
   private static final Set<String> UPLOAD_SEMANTIC_TYPES = Set.of("IMAGE", "PAINTING");
   private static final Set<String> SOURCE_TYPES = Set.of("CATALOG_REFERENCE", "USER_UPLOAD", "GENERATED", "LEGACY_IMPORT");
   private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "PRIVATE");
   private static final Set<String> STATUSES = Set.of("ACTIVE", "DELETED", "FAILED");

   private MediaAssetValues() {
   }

   public static String normalize(String value) {
      if (value != null && !value.isBlank()) {
         return value.trim().toUpperCase(Locale.ROOT);
      } else {
         throw new IllegalArgumentException("MediaAsset value must not be blank");
      }
   }

   public static String requireSupportedAssetType(String value) {
      return requireSupported(value, ASSET_TYPES, "asset type");
   }

   public static String requireSupportedSemanticType(String value) {
      return requireSupported(value, SEMANTIC_TYPES, "semantic type");
   }

   public static String requireUploadSemanticType(String value) {
      return requireSupported(value, UPLOAD_SEMANTIC_TYPES, "upload semantic type");
   }

   public static String requireSupportedSourceType(String value) {
      return requireSupported(value, SOURCE_TYPES, "source type");
   }

   public static String requireSupportedVisibility(String value) {
      return requireSupported(value, VISIBILITIES, "visibility");
   }

   public static String requireSupportedStatus(String value) {
      return requireSupported(value, STATUSES, "status");
   }

   private static String requireSupported(String value, Set<String> supported, String label) {
      String normalized = normalize(value);
      if (!supported.contains(normalized)) {
         throw new IllegalArgumentException("Unsupported MediaAsset " + label);
      } else {
         return normalized;
      }
   }

   public static final class AssetType {
      public static final String IMAGE = "IMAGE";
      public static final String AUDIO = "AUDIO";
      public static final String VIDEO = "VIDEO";
      public static final String FILE = "FILE";

      private AssetType() {
      }
   }

   public static final class SemanticType {
      public static final String IMAGE = "IMAGE";
      public static final String PAINTING = "PAINTING";
      public static final String GENERATED_PAINTING = "GENERATED_PAINTING";
      public static final String MUSIC = "MUSIC";
      public static final String VIDEO = "VIDEO";
      public static final String OTHER = "OTHER";

      private SemanticType() {
      }
   }

   public static final class SourceType {
      public static final String CATALOG_REFERENCE = "CATALOG_REFERENCE";
      public static final String USER_UPLOAD = "USER_UPLOAD";
      public static final String GENERATED = "GENERATED";
      public static final String LEGACY_IMPORT = "LEGACY_IMPORT";

      private SourceType() {
      }
   }

   public static final class Status {
      public static final String ACTIVE = "ACTIVE";
      public static final String DELETED = "DELETED";
      public static final String FAILED = "FAILED";

      private Status() {
      }
   }

   public static final class Visibility {
      public static final String PUBLIC = "PUBLIC";
      public static final String PRIVATE = "PRIVATE";

      private Visibility() {
      }
   }
}

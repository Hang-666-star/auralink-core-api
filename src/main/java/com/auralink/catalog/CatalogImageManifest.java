package com.auralink.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CatalogImageManifest {
   private static final Comparator<ImageManifestEntry> BY_FILENAME = Comparator.comparing(ImageManifestEntry::fileName);
   private final List<ImageManifestEntry> entries;
   private final Map<String, ImageManifestEntry> byFileName;
   private final Map<String, ImageManifestEntry> byLowercaseFileName;

   public CatalogImageManifest(List<ImageManifestEntry> entries) {
      if (entries == null) {
         throw new CatalogSourceException("Catalog image manifest is required");
      }

      List<ImageManifestEntry> sorted = new ArrayList<>(entries);
      sorted.sort(BY_FILENAME);
      Map<String, ImageManifestEntry> exact = new LinkedHashMap<>();
      Map<String, ImageManifestEntry> lowercase = new LinkedHashMap<>();

      for (ImageManifestEntry entry : sorted) {
         if (exact.putIfAbsent(entry.fileName(), entry) != null) {
            throw new CatalogSourceException("Catalog image manifest contains a duplicate filename");
         }

         String lowerName = entry.fileName().toLowerCase(Locale.ROOT);
         if (lowercase.putIfAbsent(lowerName, entry) != null) {
            throw new CatalogSourceException("Catalog image manifest contains case-insensitive filename ambiguity");
         }
      }

      this.entries = List.copyOf(sorted);
      this.byFileName = Map.copyOf(exact);
      this.byLowercaseFileName = Map.copyOf(lowercase);
   }

   public List<ImageManifestEntry> entries() {
      return this.entries;
   }

   public int fileCount() {
      return this.entries.size();
   }

   public boolean contains(String fileName) {
      return this.find(fileName).isPresent();
   }

   public Optional<ImageManifestEntry> find(String fileName) {
      return fileName == null ? Optional.empty() : Optional.ofNullable(this.byFileName.get(fileName));
   }

   public Optional<ImageManifestEntry> findIgnoringCase(String fileName) {
      return fileName == null ? Optional.empty() : Optional.ofNullable(this.byLowercaseFileName.get(fileName.toLowerCase(Locale.ROOT)));
   }
}

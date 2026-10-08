package com.auralink.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record CatalogSourceSnapshot(String sourceName, String csvSha256, String fingerprint, List<CatalogSourceRow> rows, List<String> orphanImageFileNames) {
   private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

   public CatalogSourceSnapshot(String sourceName, String csvSha256, String fingerprint, List<CatalogSourceRow> rows, List<String> orphanImageFileNames) {
      sourceName = sourceName == null ? "" : sourceName.trim();
      if (sourceName.isBlank()) {
         throw new CatalogSourceException("Catalog snapshot source name is required");
      }

      if (csvSha256 == null || !SHA256.matcher(csvSha256).matches() || fingerprint == null || !SHA256.matcher(fingerprint).matches()) {
         throw new CatalogSourceException("Catalog snapshot fingerprints must be lowercase SHA-256 values");
      }

      if (rows != null && orphanImageFileNames != null) {
         rows = List.copyOf(rows);
         List<String> sortedOrphans = new ArrayList<>(orphanImageFileNames);
         sortedOrphans.sort(String::compareTo);
         orphanImageFileNames = List.copyOf(sortedOrphans);
         this.sourceName = sourceName;
         this.csvSha256 = csvSha256;
         this.fingerprint = fingerprint;
         this.rows = rows;
         this.orphanImageFileNames = orphanImageFileNames;
      } else {
         throw new CatalogSourceException("Catalog snapshot rows and orphan manifest are required");
      }
   }

   public int totalRows() {
      return this.rows.size();
   }

   public int matchedImages() {
      return (int)this.rows.stream().filter(CatalogSourceRow::imageAvailable).count();
   }

   public int missingImages() {
      return this.totalRows() - this.matchedImages();
   }

   public int orphanImages() {
      return this.orphanImageFileNames.size();
   }
}

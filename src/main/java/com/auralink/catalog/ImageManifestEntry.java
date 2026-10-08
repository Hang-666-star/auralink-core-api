package com.auralink.catalog;

public record ImageManifestEntry(String fileName, long size, long lastModifiedMillis) {
   public ImageManifestEntry(String fileName, long size, long lastModifiedMillis) {
      if (fileName == null
         || fileName.isBlank()
         || fileName.indexOf(47) >= 0
         || fileName.indexOf(92) >= 0
         || fileName.codePoints().anyMatch(Character::isISOControl)) {
         throw new CatalogSourceException("Catalog image manifest contains an invalid filename");
      }

      if (size >= 0L && lastModifiedMillis >= 0L) {
         this.fileName = fileName;
         this.size = size;
         this.lastModifiedMillis = lastModifiedMillis;
      } else {
         throw new CatalogSourceException("Catalog image manifest contains invalid metadata");
      }
   }
}

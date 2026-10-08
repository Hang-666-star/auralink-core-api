package com.auralink.catalog;

public record CatalogSourceRow(OfficialPaintingRecord record, String imageFileName) {
   public CatalogSourceRow(OfficialPaintingRecord record, String imageFileName) {
      if (record == null) {
         throw new CatalogSourceException("Catalog source row requires an official painting record");
      }

      if (imageFileName != null) {
         imageFileName = imageFileName.trim();
         if (imageFileName.isBlank()
            || imageFileName.indexOf(47) >= 0
            || imageFileName.indexOf(92) >= 0
            || imageFileName.codePoints().anyMatch(Character::isISOControl)) {
            throw new CatalogSourceException("Catalog source row contains an unsafe image filename");
         }
      }

      this.record = record;
      this.imageFileName = imageFileName;
   }

   public boolean imageAvailable() {
      return this.imageFileName != null;
   }
}

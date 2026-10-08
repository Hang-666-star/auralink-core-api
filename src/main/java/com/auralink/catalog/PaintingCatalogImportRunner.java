package com.auralink.catalog;

import com.auralink.config.properties.PaintingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PaintingCatalogImportRunner implements ApplicationRunner {
   private static final Logger log = LoggerFactory.getLogger(PaintingCatalogImportRunner.class);
   private final PaintingProperties properties;
   private final PaintingCatalogImporter importer;

   public void run(ApplicationArguments arguments) {
      if (!this.properties.isImportEnabled()) {
         log.info("Official painting catalog startup import is disabled");
      } else {
         try {
            CatalogImportResult result = this.importer.importCatalog();
            log.info(
               "Official painting catalog import completed: status={}, total={}, inserted={}, updated={}, unchanged={}",
               new Object[]{result.status(), result.totalRows(), result.insertedRows(), result.updatedRows(), result.unchangedRows()}
            );
         } catch (RuntimeException exception) {
            log.error("Official painting catalog import failed; type={}", exception.getClass().getSimpleName());
            if (this.properties.isImportFailOnError()) {
               throw exception;
            }
         }
      }
   }

   public PaintingCatalogImportRunner(final PaintingProperties properties, final PaintingCatalogImporter importer) {
      this.properties = properties;
      this.importer = importer;
   }
}

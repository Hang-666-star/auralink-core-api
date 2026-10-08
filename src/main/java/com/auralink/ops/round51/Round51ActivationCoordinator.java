package com.auralink.ops.round51;

import com.auralink.catalog.CatalogImportResult;
import com.auralink.catalog.CatalogSourceSnapshot;
import com.auralink.catalog.CatalogSourceSnapshotFactory;
import com.auralink.catalog.PaintingCatalogImporter;
import com.auralink.config.properties.PaintingProperties;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.BaselineResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public final class Round51ActivationCoordinator {
   private static final String MIGRATION_LOCATION = "classpath:db/migration";
   private static final MigrationVersion ROUND51_TARGET_VERSION = MigrationVersion.fromVersion("2");
   private final DataSource dataSource;
   private final PaintingCatalogImporter importer;
   private final CatalogSourceSnapshotFactory snapshotFactory;
   private final PaintingProperties paintingProperties;
   private final Round51DatabaseVerifier verifier;
   private final Round51DatabaseVerifier.Expectations expected;
   private final Round51ActivationCoordinator.ActivationFaultInjector faultInjector;

   @Autowired
   public Round51ActivationCoordinator(
      DataSource dataSource, PaintingCatalogImporter importer, CatalogSourceSnapshotFactory snapshotFactory, PaintingProperties paintingProperties
   ) {
      this(dataSource, importer, snapshotFactory, paintingProperties, Round51DatabaseVerifier.Expectations.production(), checkpoint -> {});
   }

   Round51ActivationCoordinator(
      DataSource dataSource,
      PaintingCatalogImporter importer,
      CatalogSourceSnapshotFactory snapshotFactory,
      PaintingProperties paintingProperties,
      Round51DatabaseVerifier.Expectations expected,
      Round51ActivationCoordinator.ActivationFaultInjector faultInjector
   ) {
      this.dataSource = dataSource;
      this.importer = importer;
      this.snapshotFactory = snapshotFactory;
      this.paintingProperties = paintingProperties;
      this.verifier = new Round51DatabaseVerifier(new JdbcTemplate(dataSource));
      this.expected = expected;
      this.faultInjector = faultInjector;
   }

   public Round51ActivationResult activate() {
      CatalogSourceSnapshot snapshot = this.snapshotFactory
         .create(Path.of(this.paintingProperties.getMetadataCsvPath()), Path.of(this.paintingProperties.getPictureDir()));
      this.verifier.verifySnapshot(snapshot, this.expected);
      Round51ActivationState state = this.verifier.classify(snapshot, this.expected);
      if (state == Round51ActivationState.ALREADY_ACTIVATED_HEALTHY) {
         Flyway flyway = this.configuredFlyway();
         ValidateResult validation = flyway.validateWithResult();
         this.require(validation.validationSuccessful, "FLYWAY_VALIDATE_FAILED");
         this.require(flyway.info().pending().length == 0, "FLYWAY_PENDING_MIGRATIONS_FOUND");
         return this.result(state, snapshot);
      }

      if (state != Round51ActivationState.INHERITED_READY) {
         throw new Round51ActivationException(
            "PARTIALLY_ACTIVATED_STATE_REFUSED", "Database state is neither the exact inherited state nor a healthy activated state"
         );
      }

      String legacyDigest = this.verifier.legacyDigest();
      Flyway flyway = this.configuredFlyway();

      try {
         BaselineResult baseline = flyway.baseline();
         this.require(baseline.successfullyBaselined && "1".equals(baseline.baselineVersion), "FLYWAY_BASELINE_FAILED");
         this.verifier.verifyBaseline();
         this.faultInjector.after(Round51ActivationCoordinator.ActivationCheckpoint.AFTER_BASELINE);
         MigrateResult firstMigrate = flyway.migrate();
         this.require(firstMigrate.success && firstMigrate.migrationsExecuted == 1, "FLYWAY_V2_MIGRATION_FAILED");
         ValidateResult validation = flyway.validateWithResult();
         this.require(validation.validationSuccessful, "FLYWAY_VALIDATE_FAILED");
         MigrateResult repeatMigrate = flyway.migrate();
         this.require(repeatMigrate.success && repeatMigrate.migrationsExecuted == 0, "FLYWAY_REPEAT_MIGRATE_FAILED");
         this.verifier.verifyMigratedEmpty(this.expected);
         this.verifier.verifyLegacyDigest(legacyDigest);
         this.faultInjector.after(Round51ActivationCoordinator.ActivationCheckpoint.AFTER_MIGRATE);
         System.out.println("CATALOG_IMPORT_STARTED_LONG_RUNNING");
         this.faultInjector.after(Round51ActivationCoordinator.ActivationCheckpoint.BEFORE_CATALOG_IMPORT);
         CatalogImportResult firstImport = this.importer.importCatalog(snapshot);
         this.verifier.verifyFirstImport(firstImport, snapshot, this.expected);
         String identityDigest = this.verifier.identityDigest();
         CatalogSourceSnapshot reimportSnapshot = this.snapshotFactory
            .create(Path.of(this.paintingProperties.getMetadataCsvPath()), Path.of(this.paintingProperties.getPictureDir()));
         this.verifier.verifySnapshot(reimportSnapshot, this.expected);
         this.require(snapshot.fingerprint().equals(reimportSnapshot.fingerprint()), "CATALOG_SOURCE_CHANGED_DURING_ACTIVATION");
         CatalogImportResult secondImport = this.importer.importCatalog(reimportSnapshot);
         this.verifier.verifySecondImport(secondImport, reimportSnapshot, this.expected, identityDigest);
         this.verifier.verifyLegacyDigest(legacyDigest);
         return this.result(Round51ActivationState.ACTIVATED_NOW, reimportSnapshot);
      } catch (Round51ActivationException exception) {
         throw exception;
      } catch (RuntimeException exception) {
         throw new Round51ActivationException("ACTIVATION_PHASE_FAILED", "Controlled activation phase failed", exception);
      }
   }

   private Flyway configuredFlyway() {
      return Flyway.configure()
         .dataSource(this.dataSource)
         .locations(new String[]{"classpath:db/migration"})
         .baselineOnMigrate(false)
         .baselineVersion(MigrationVersion.fromVersion("1"))
         .target(ROUND51_TARGET_VERSION)
         .baselineDescription("Inherited legacy schema baseline")
         .validateOnMigrate(true)
         .validateMigrationNaming(true)
         .cleanDisabled(true)
         .initSql("PRAGMA foreign_keys=ON")
         .load();
   }

   private Round51ActivationResult result(Round51ActivationState state, CatalogSourceSnapshot snapshot) {
      return new Round51ActivationResult(state, snapshot.fingerprint(), this.expected.paintings(), this.expected.catalogMediaAssets());
   }

   private void require(boolean condition, String code) {
      if (!condition) {
         throw new Round51ActivationException(code, "Flyway activation phase failed");
      }
   }

   enum ActivationCheckpoint {
      AFTER_BASELINE,
      AFTER_MIGRATE,
      BEFORE_CATALOG_IMPORT;
   }

   @FunctionalInterface
   interface ActivationFaultInjector {
      void after(Round51ActivationCoordinator.ActivationCheckpoint checkpoint);
   }
}

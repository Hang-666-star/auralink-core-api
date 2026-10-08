package com.auralink.catalog;

import com.auralink.config.properties.PaintingProperties;
import com.auralink.entity.CatalogImportRun;
import com.auralink.entity.MediaAsset;
import com.auralink.entity.Painting;
import com.auralink.repository.CatalogImportRunRepository;
import com.auralink.repository.PaintingRepository;
import com.auralink.service.media.MediaAssetService;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaintingCatalogImporter {
   private static final String PAINTING_STATUS_ACTIVE = "ACTIVE";
   private final PaintingProperties properties;
   private final CatalogSourceSnapshotFactory snapshotFactory;
   private final DynastyNormalizer dynastyNormalizer;
   private final PaintingRepository paintingRepository;
   private final CatalogImportRunRepository importRunRepository;
   private final MediaAssetService mediaAssetService;
   private final PlatformTransactionManager transactionManager;

   public CatalogImportResult importCatalog() {
      try {
         Path csvPath = Path.of(this.properties.getMetadataCsvPath()).toAbsolutePath().normalize();
         Path pictureDirectory = Path.of(this.properties.getPictureDir()).toAbsolutePath().normalize();
         return this.importCatalog(this.snapshotFactory.create(csvPath, pictureDirectory));
      } catch (InvalidPathException exception) {
         throw new CatalogImportException("Catalog import configuration is invalid", exception);
      }
   }

   public synchronized CatalogImportResult importCatalog(CatalogSourceSnapshot snapshot) {
      Objects.requireNonNull(snapshot, "Catalog source snapshot is required");
      CatalogImportRun previous = this.importRunRepository.findTopBySourceSha256AndStatusOrderByFinishedAtDesc(snapshot.fingerprint(), "SUCCESS").orElse(null);
      if (previous != null) {
         CatalogImportRun skipped = this.inNewTransaction(() -> this.saveSkippedRun(snapshot));
         return CatalogImportResult.from(skipped);
      }

      CatalogImportRun running = this.inNewTransaction(() -> this.saveRunningRun(snapshot));
      PaintingCatalogImporter.ImportCounters counters = new PaintingCatalogImporter.ImportCounters();

      try {
         int batchSize = this.properties.getImportBatchSize();
         List<CatalogSourceRow> rows = snapshot.rows();

         for (int start = 0; start < rows.size(); start += batchSize) {
            int end = Math.min(start + batchSize, rows.size());
            List<CatalogSourceRow> batch = rows.subList(start, end);
            PaintingCatalogImporter.BatchCounts batchCounts = this.inNewTransaction(() -> this.importBatch(batch));
            counters.add(batchCounts);
            this.inNewTransaction(() -> this.updateRunProgress(running.getId(), snapshot, counters));
         }

         CatalogImportRun completed = this.inNewTransaction(() -> this.finishRun(running.getId(), snapshot, counters, "SUCCESS", null));
         return CatalogImportResult.from(completed);
      } catch (RuntimeException var12) {
         RuntimeException exception = var12;

         try {
            this.inNewTransaction(() -> this.finishRun(running.getId(), snapshot, counters, "FAILED", this.safeFailureMessage(exception)));
         } catch (RuntimeException auditFailure) {
            var12.addSuppressed(auditFailure);
         }

         throw new CatalogImportException("Official painting catalog import failed", var12);
      }
   }

   private PaintingCatalogImporter.BatchCounts importBatch(List<CatalogSourceRow> rows) {
      int inserted = 0;
      int updated = 0;
      int unchanged = 0;

      for (CatalogSourceRow sourceRow : rows) {
         MediaAsset imageAsset = sourceRow.imageAvailable() ? this.mediaAssetService.registerCatalogReference(sourceRow.imageFileName()) : null;
         OfficialPaintingRecord record = sourceRow.record();
         Painting existing = this.paintingRepository.findBySourceKey(record.sourceKey()).orElse(null);
         if (existing == null) {
            this.paintingRepository.save(this.newPainting(record, imageAsset));
            inserted++;
         } else if (this.matches(existing, record, imageAsset)) {
            unchanged++;
         } else {
            this.apply(existing, record, imageAsset);
            this.paintingRepository.save(existing);
            updated++;
         }
      }

      this.paintingRepository.flush();
      return new PaintingCatalogImporter.BatchCounts(inserted, updated, unchanged);
   }

   private Painting newPainting(OfficialPaintingRecord record, MediaAsset imageAsset) {
      Painting painting = Painting.builder().build();
      this.apply(painting, record, imageAsset);
      return painting;
   }

   private void apply(Painting painting, OfficialPaintingRecord record, MediaAsset imageAsset) {
      painting.setSourceKey(record.sourceKey());
      painting.setSourceSequence(record.sourceSequence());
      painting.setImageStorageName(record.imageStorageName());
      painting.setTitle(record.title());
      painting.setAuthorName(record.authorName());
      painting.setAuthorBirthYear(record.authorBirthYear());
      painting.setAuthorBirthPlace(record.authorBirthPlace());
      painting.setAuthorSchool(record.authorSchool());
      painting.setCreationYear(record.creationYear());
      painting.setCreationDynastyRaw(record.creationDynastyRaw());
      painting.setCreationDynastyNormalized(this.normalizedDynasty(record));
      painting.setActualSize(record.actualSize());
      painting.setCollectionInstitution(record.collectionInstitution());
      painting.setCategory(record.category());
      painting.setSubject(record.subject());
      painting.setPaintingSchool(record.paintingSchool());
      painting.setStyle(record.style());
      painting.setColor(record.color());
      painting.setComposition(record.composition());
      painting.setArtisticConception(record.artisticConception());
      painting.setBrushwork(record.brushwork());
      painting.setInkMethod(record.inkMethod());
      painting.setPaintingMaterial(record.paintingMaterial());
      painting.setPigment(record.pigment());
      painting.setSeal(record.seal());
      painting.setCulturalSymbol(record.culturalSymbol());
      painting.setGeneratedText(record.generatedText());
      painting.setMusicSceneDescription(record.musicSceneDescription());
      painting.setCollectionPlatform(record.collectionPlatform());
      painting.setImageAsset(imageAsset);
      painting.setImageAvailable(imageAsset != null);
      painting.setVisibleInGallery(imageAsset != null);
      painting.setStatus("ACTIVE");
   }

   private boolean matches(Painting painting, OfficialPaintingRecord record, MediaAsset imageAsset) {
      return Objects.equals(painting.getSourceKey(), record.sourceKey())
         && Objects.equals(painting.getSourceSequence(), record.sourceSequence())
         && Objects.equals(painting.getImageStorageName(), record.imageStorageName())
         && Objects.equals(painting.getTitle(), record.title())
         && Objects.equals(painting.getAuthorName(), record.authorName())
         && Objects.equals(painting.getAuthorBirthYear(), record.authorBirthYear())
         && Objects.equals(painting.getAuthorBirthPlace(), record.authorBirthPlace())
         && Objects.equals(painting.getAuthorSchool(), record.authorSchool())
         && Objects.equals(painting.getCreationYear(), record.creationYear())
         && Objects.equals(painting.getCreationDynastyRaw(), record.creationDynastyRaw())
         && Objects.equals(painting.getCreationDynastyNormalized(), this.normalizedDynasty(record))
         && Objects.equals(painting.getActualSize(), record.actualSize())
         && Objects.equals(painting.getCollectionInstitution(), record.collectionInstitution())
         && Objects.equals(painting.getCategory(), record.category())
         && Objects.equals(painting.getSubject(), record.subject())
         && Objects.equals(painting.getPaintingSchool(), record.paintingSchool())
         && Objects.equals(painting.getStyle(), record.style())
         && Objects.equals(painting.getColor(), record.color())
         && Objects.equals(painting.getComposition(), record.composition())
         && Objects.equals(painting.getArtisticConception(), record.artisticConception())
         && Objects.equals(painting.getBrushwork(), record.brushwork())
         && Objects.equals(painting.getInkMethod(), record.inkMethod())
         && Objects.equals(painting.getPaintingMaterial(), record.paintingMaterial())
         && Objects.equals(painting.getPigment(), record.pigment())
         && Objects.equals(painting.getSeal(), record.seal())
         && Objects.equals(painting.getCulturalSymbol(), record.culturalSymbol())
         && Objects.equals(painting.getGeneratedText(), record.generatedText())
         && Objects.equals(painting.getMusicSceneDescription(), record.musicSceneDescription())
         && Objects.equals(painting.getCollectionPlatform(), record.collectionPlatform())
         && Objects.equals(this.entityId(painting.getImageAsset()), this.entityId(imageAsset))
         && painting.isImageAvailable() == (imageAsset != null)
         && painting.isVisibleInGallery() == (imageAsset != null)
         && Objects.equals(painting.getStatus(), "ACTIVE");
   }

   private Long entityId(MediaAsset asset) {
      return asset == null ? null : asset.getId();
   }

   private String normalizedDynasty(OfficialPaintingRecord record) {
      return this.dynastyNormalizer.normalize(record.creationDynastyRaw());
   }

   private CatalogImportRun saveRunningRun(CatalogSourceSnapshot snapshot) {
      CatalogImportRun run = this.baseRun(snapshot, "RUNNING");
      return (CatalogImportRun)this.importRunRepository.saveAndFlush(run);
   }

   private CatalogImportRun saveSkippedRun(CatalogSourceSnapshot snapshot) {
      CatalogImportRun run = this.baseRun(snapshot, "SKIPPED");
      run.setUnchangedRows(snapshot.totalRows());
      run.setFinishedAt(LocalDateTime.now());
      return (CatalogImportRun)this.importRunRepository.saveAndFlush(run);
   }

   private CatalogImportRun baseRun(CatalogSourceSnapshot snapshot, String status) {
      return CatalogImportRun.builder()
         .sourceName(snapshot.sourceName())
         .sourceSha256(snapshot.fingerprint())
         .totalRows(snapshot.totalRows())
         .matchedImages(snapshot.matchedImages())
         .missingImages(snapshot.missingImages())
         .orphanImages(snapshot.orphanImages())
         .status(status)
         .startedAt(LocalDateTime.now())
         .build();
   }

   private CatalogImportRun updateRunProgress(Long runId, CatalogSourceSnapshot snapshot, PaintingCatalogImporter.ImportCounters counters) {
      CatalogImportRun run = this.requireRun(runId);
      this.copyCounts(run, snapshot, counters);
      return (CatalogImportRun)this.importRunRepository.saveAndFlush(run);
   }

   private CatalogImportRun finishRun(
      Long runId, CatalogSourceSnapshot snapshot, PaintingCatalogImporter.ImportCounters counters, String status, String errorMessage
   ) {
      CatalogImportRun run = this.requireRun(runId);
      this.copyCounts(run, snapshot, counters);
      run.setStatus(status);
      run.setFinishedAt(LocalDateTime.now());
      run.setErrorMessage(errorMessage);
      return (CatalogImportRun)this.importRunRepository.saveAndFlush(run);
   }

   private void copyCounts(CatalogImportRun run, CatalogSourceSnapshot snapshot, PaintingCatalogImporter.ImportCounters counters) {
      run.setTotalRows(snapshot.totalRows());
      run.setInsertedRows(counters.inserted);
      run.setUpdatedRows(counters.updated);
      run.setUnchangedRows(counters.unchanged);
      run.setMatchedImages(snapshot.matchedImages());
      run.setMissingImages(snapshot.missingImages());
      run.setOrphanImages(snapshot.orphanImages());
   }

   private CatalogImportRun requireRun(Long runId) {
      return (CatalogImportRun)this.importRunRepository.findById(runId).orElseThrow(() -> new IllegalStateException("Catalog import audit row is unavailable"));
   }

   private String safeFailureMessage(RuntimeException exception) {
      return "Catalog synchronization failed (" + exception.getClass().getSimpleName() + ")";
   }

   private <T> T inNewTransaction(Supplier<T> operation) {
      TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
      transaction.setPropagationBehavior(3);
      T result = (T)transaction.execute(status -> operation.get());
      if (result == null) {
         throw new IllegalStateException("Catalog transaction returned no result");
      } else {
         return result;
      }
   }

   public PaintingCatalogImporter(
      final PaintingProperties properties,
      final CatalogSourceSnapshotFactory snapshotFactory,
      final DynastyNormalizer dynastyNormalizer,
      final PaintingRepository paintingRepository,
      final CatalogImportRunRepository importRunRepository,
      final MediaAssetService mediaAssetService,
      final PlatformTransactionManager transactionManager
   ) {
      this.properties = properties;
      this.snapshotFactory = snapshotFactory;
      this.dynastyNormalizer = dynastyNormalizer;
      this.paintingRepository = paintingRepository;
      this.importRunRepository = importRunRepository;
      this.mediaAssetService = mediaAssetService;
      this.transactionManager = transactionManager;
   }

   private record BatchCounts(int inserted, int updated, int unchanged) {
   }

   private static final class ImportCounters {
      private int inserted;
      private int updated;
      private int unchanged;

      private void add(PaintingCatalogImporter.BatchCounts counts) {
         this.inserted = this.inserted + counts.inserted();
         this.updated = this.updated + counts.updated();
         this.unchanged = this.unchanged + counts.unchanged();
      }
   }
}

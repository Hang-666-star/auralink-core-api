package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
   name = "catalog_import_runs",
   uniqueConstraints = @UniqueConstraint(name = "uq_catalog_import_runs_public_id", columnNames = "public_id"),
   indexes = {
         @Index(name = "idx_catalog_import_runs_status_started", columnList = "status, started_at"),
         @Index(name = "idx_catalog_import_runs_source_sha256", columnList = "source_sha256")
   }
)
public class CatalogImportRun extends BasePublicIdEntity {
   @Column(name = "source_name", nullable = false, length = 512)
   private String sourceName;
   @Column(name = "source_sha256", nullable = false, length = 64)
   private String sourceSha256;
   @Column(name = "total_rows", nullable = false)
   private int totalRows;
   @Column(name = "inserted_rows", nullable = false)
   private int insertedRows;
   @Column(name = "updated_rows", nullable = false)
   private int updatedRows;
   @Column(name = "unchanged_rows", nullable = false)
   private int unchangedRows;
   @Column(name = "matched_images", nullable = false)
   private int matchedImages;
   @Column(name = "missing_images", nullable = false)
   private int missingImages;
   @Column(name = "orphan_images", nullable = false)
   private int orphanImages;
   @Column(nullable = false, length = 64)
   private String status;
   @Column(name = "started_at", nullable = false)
   private LocalDateTime startedAt;
   @Column(name = "finished_at")
   private LocalDateTime finishedAt;
   @Column(name = "error_message", columnDefinition = "TEXT")
   private String errorMessage;

   @PrePersist
   protected void initializeStartedAt() {
      if (this.startedAt == null) {
         this.startedAt = LocalDateTime.now();
      }
   }

   private static int $default$totalRows() {
      return 0;
   }

   private static int $default$insertedRows() {
      return 0;
   }

   private static int $default$updatedRows() {
      return 0;
   }

   private static int $default$unchangedRows() {
      return 0;
   }

   private static int $default$matchedImages() {
      return 0;
   }

   private static int $default$missingImages() {
      return 0;
   }

   private static int $default$orphanImages() {
      return 0;
   }

   protected CatalogImportRun(final CatalogImportRun.CatalogImportRunBuilder<?, ?> b) {
      super(b);
      this.sourceName = b.sourceName;
      this.sourceSha256 = b.sourceSha256;
      if (b.totalRows$set) {
         this.totalRows = b.totalRows$value;
      } else {
         this.totalRows = $default$totalRows();
      }

      if (b.insertedRows$set) {
         this.insertedRows = b.insertedRows$value;
      } else {
         this.insertedRows = $default$insertedRows();
      }

      if (b.updatedRows$set) {
         this.updatedRows = b.updatedRows$value;
      } else {
         this.updatedRows = $default$updatedRows();
      }

      if (b.unchangedRows$set) {
         this.unchangedRows = b.unchangedRows$value;
      } else {
         this.unchangedRows = $default$unchangedRows();
      }

      if (b.matchedImages$set) {
         this.matchedImages = b.matchedImages$value;
      } else {
         this.matchedImages = $default$matchedImages();
      }

      if (b.missingImages$set) {
         this.missingImages = b.missingImages$value;
      } else {
         this.missingImages = $default$missingImages();
      }

      if (b.orphanImages$set) {
         this.orphanImages = b.orphanImages$value;
      } else {
         this.orphanImages = $default$orphanImages();
      }

      this.status = b.status;
      this.startedAt = b.startedAt;
      this.finishedAt = b.finishedAt;
      this.errorMessage = b.errorMessage;
   }

   public static CatalogImportRun.CatalogImportRunBuilder<?, ?> builder() {
      return new CatalogImportRun.CatalogImportRunBuilderImpl();
   }

   public String getSourceName() {
      return this.sourceName;
   }

   public String getSourceSha256() {
      return this.sourceSha256;
   }

   public int getTotalRows() {
      return this.totalRows;
   }

   public int getInsertedRows() {
      return this.insertedRows;
   }

   public int getUpdatedRows() {
      return this.updatedRows;
   }

   public int getUnchangedRows() {
      return this.unchangedRows;
   }

   public int getMatchedImages() {
      return this.matchedImages;
   }

   public int getMissingImages() {
      return this.missingImages;
   }

   public int getOrphanImages() {
      return this.orphanImages;
   }

   public String getStatus() {
      return this.status;
   }

   public LocalDateTime getStartedAt() {
      return this.startedAt;
   }

   public LocalDateTime getFinishedAt() {
      return this.finishedAt;
   }

   public String getErrorMessage() {
      return this.errorMessage;
   }

   public void setSourceName(final String sourceName) {
      this.sourceName = sourceName;
   }

   public void setSourceSha256(final String sourceSha256) {
      this.sourceSha256 = sourceSha256;
   }

   public void setTotalRows(final int totalRows) {
      this.totalRows = totalRows;
   }

   public void setInsertedRows(final int insertedRows) {
      this.insertedRows = insertedRows;
   }

   public void setUpdatedRows(final int updatedRows) {
      this.updatedRows = updatedRows;
   }

   public void setUnchangedRows(final int unchangedRows) {
      this.unchangedRows = unchangedRows;
   }

   public void setMatchedImages(final int matchedImages) {
      this.matchedImages = matchedImages;
   }

   public void setMissingImages(final int missingImages) {
      this.missingImages = missingImages;
   }

   public void setOrphanImages(final int orphanImages) {
      this.orphanImages = orphanImages;
   }

   public void setStatus(final String status) {
      this.status = status;
   }

   public void setStartedAt(final LocalDateTime startedAt) {
      this.startedAt = startedAt;
   }

   public void setFinishedAt(final LocalDateTime finishedAt) {
      this.finishedAt = finishedAt;
   }

   public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public CatalogImportRun() {
      this.totalRows = $default$totalRows();
      this.insertedRows = $default$insertedRows();
      this.updatedRows = $default$updatedRows();
      this.unchangedRows = $default$unchangedRows();
      this.matchedImages = $default$matchedImages();
      this.missingImages = $default$missingImages();
      this.orphanImages = $default$orphanImages();
   }

   public CatalogImportRun(
      final String sourceName,
      final String sourceSha256,
      final int totalRows,
      final int insertedRows,
      final int updatedRows,
      final int unchangedRows,
      final int matchedImages,
      final int missingImages,
      final int orphanImages,
      final String status,
      final LocalDateTime startedAt,
      final LocalDateTime finishedAt,
      final String errorMessage
   ) {
      this.sourceName = sourceName;
      this.sourceSha256 = sourceSha256;
      this.totalRows = totalRows;
      this.insertedRows = insertedRows;
      this.updatedRows = updatedRows;
      this.unchangedRows = unchangedRows;
      this.matchedImages = matchedImages;
      this.missingImages = missingImages;
      this.orphanImages = orphanImages;
      this.status = status;
      this.startedAt = startedAt;
      this.finishedAt = finishedAt;
      this.errorMessage = errorMessage;
   }

   public abstract static class CatalogImportRunBuilder<C extends CatalogImportRun, B extends CatalogImportRun.CatalogImportRunBuilder<C, B>>
      extends BasePublicIdEntity.BasePublicIdEntityBuilder<C, B> {
      private String sourceName;
      private String sourceSha256;
      private boolean totalRows$set;
      private int totalRows$value;
      private boolean insertedRows$set;
      private int insertedRows$value;
      private boolean updatedRows$set;
      private int updatedRows$value;
      private boolean unchangedRows$set;
      private int unchangedRows$value;
      private boolean matchedImages$set;
      private int matchedImages$value;
      private boolean missingImages$set;
      private int missingImages$value;
      private boolean orphanImages$set;
      private int orphanImages$value;
      private String status;
      private LocalDateTime startedAt;
      private LocalDateTime finishedAt;
      private String errorMessage;

      public B sourceName(final String sourceName) {
         this.sourceName = sourceName;
         return this.self();
      }

      public B sourceSha256(final String sourceSha256) {
         this.sourceSha256 = sourceSha256;
         return this.self();
      }

      public B totalRows(final int totalRows) {
         this.totalRows$value = totalRows;
         this.totalRows$set = true;
         return this.self();
      }

      public B insertedRows(final int insertedRows) {
         this.insertedRows$value = insertedRows;
         this.insertedRows$set = true;
         return this.self();
      }

      public B updatedRows(final int updatedRows) {
         this.updatedRows$value = updatedRows;
         this.updatedRows$set = true;
         return this.self();
      }

      public B unchangedRows(final int unchangedRows) {
         this.unchangedRows$value = unchangedRows;
         this.unchangedRows$set = true;
         return this.self();
      }

      public B matchedImages(final int matchedImages) {
         this.matchedImages$value = matchedImages;
         this.matchedImages$set = true;
         return this.self();
      }

      public B missingImages(final int missingImages) {
         this.missingImages$value = missingImages;
         this.missingImages$set = true;
         return this.self();
      }

      public B orphanImages(final int orphanImages) {
         this.orphanImages$value = orphanImages;
         this.orphanImages$set = true;
         return this.self();
      }

      public B status(final String status) {
         this.status = status;
         return this.self();
      }

      public B startedAt(final LocalDateTime startedAt) {
         this.startedAt = startedAt;
         return this.self();
      }

      public B finishedAt(final LocalDateTime finishedAt) {
         this.finishedAt = finishedAt;
         return this.self();
      }

      public B errorMessage(final String errorMessage) {
         this.errorMessage = errorMessage;
         return this.self();
      }

      protected abstract B self();

      public abstract C build();

      @Override
      public String toString() {
         return "CatalogImportRun.CatalogImportRunBuilder(super="
            + super.toString()
            + ", sourceName="
            + this.sourceName
            + ", sourceSha256="
            + this.sourceSha256
            + ", totalRows$value="
            + this.totalRows$value
            + ", insertedRows$value="
            + this.insertedRows$value
            + ", updatedRows$value="
            + this.updatedRows$value
            + ", unchangedRows$value="
            + this.unchangedRows$value
            + ", matchedImages$value="
            + this.matchedImages$value
            + ", missingImages$value="
            + this.missingImages$value
            + ", orphanImages$value="
            + this.orphanImages$value
            + ", status="
            + this.status
            + ", startedAt="
            + this.startedAt
            + ", finishedAt="
            + this.finishedAt
            + ", errorMessage="
            + this.errorMessage
            + ")";
      }
   }

   private static final class CatalogImportRunBuilderImpl
      extends CatalogImportRun.CatalogImportRunBuilder<CatalogImportRun, CatalogImportRun.CatalogImportRunBuilderImpl> {
      protected CatalogImportRun.CatalogImportRunBuilderImpl self() {
         return this;
      }

      @Override
      public CatalogImportRun build() {
         return new CatalogImportRun(this);
      }
   }
}

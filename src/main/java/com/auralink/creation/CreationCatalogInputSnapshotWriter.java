package com.auralink.creation;

import com.auralink.entity.Creation;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile({"catalog-postgres-creation-integration", "catalog-postgres-production"})
public class CreationCatalogInputSnapshotWriter {
   private final JdbcTemplate jdbc;

   public CreationCatalogInputSnapshotWriter(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public void capture(Creation creation) {
      if (creation.getSourcePainting() != null) {
         int inserted = this.jdbc
            .update(
               "INSERT INTO creation_catalog_input_snapshots(\n    creation_id, catalog_painting_id, catalog_asset_id, input_batch_id,\n    input_revision, projected_painting_id, snapshot_json)\nSELECT ?, projection.catalog_painting_id, projection.catalog_asset_id,\n       projection.input_batch_id, projection.input_revision,\n       projection.jpa_painting_id,\n       jsonb_build_object(\n           'schemaVersion', 1,\n           'catalogPaintingId', projection.catalog_painting_id,\n           'catalogAssetId', projection.catalog_asset_id,\n           'inputBatchId', projection.input_batch_id,\n           'inputRevision', projection.input_revision,\n           'title', painting.title,\n           'authorName', painting.author_name,\n           'category', painting.category)\n  FROM catalog_creation_painting_projection projection\n  JOIN paintings painting ON painting.id = projection.jpa_painting_id\n WHERE projection.jpa_painting_id = ?\nON CONFLICT (creation_id) DO NOTHING\n",
               new Object[]{creation.getId(), creation.getSourcePainting().getId()}
            );
         if (inserted != 1) {
            throw new IllegalStateException("Catalog Creation input lacks an immutable projection snapshot");
         }
      }
   }
}

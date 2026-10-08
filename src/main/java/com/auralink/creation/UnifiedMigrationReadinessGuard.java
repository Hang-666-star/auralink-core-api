package com.auralink.creation;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile({"catalog-postgres-creation-integration", "catalog-postgres-production"})
@ConditionalOnProperty(prefix = "auralink.unified-import", name = "require-succeeded", havingValue = "true")
public class UnifiedMigrationReadinessGuard {
   private final JdbcTemplate jdbc;
   private final String sourceId;

   public UnifiedMigrationReadinessGuard(JdbcTemplate jdbc, @Value("${auralink.unified-import.expected-source-id}") String sourceId) {
      this.jdbc = jdbc;
      this.sourceId = sourceId;
   }

   @PostConstruct
   void requireCompletedUnifiedImport() {
      Boolean ready = (Boolean)this.jdbc
         .queryForObject(
            "SELECT EXISTS (\n    SELECT 1\n      FROM catalog_unified_migration_runs\n     WHERE source_id = ? AND status = 'SUCCEEDED'\n)\n",
            Boolean.class,
            new Object[]{this.sourceId}
         );
      if (!Boolean.TRUE.equals(ready)) {
         throw new IllegalStateException("local unified PostgreSQL import is not validated; refusing application startup");
      }
   }
}

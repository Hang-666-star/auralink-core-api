package com.auralink.catalogguide;

import com.auralink.catalogread.CatalogReadStore;
import com.auralink.config.properties.GuideProperties;
import com.auralink.guide.context.PaintingGuideContextBuilder;
import com.auralink.guide.hash.GuideSourceHasher;
import com.auralink.guide.knowledge.KnowledgeContextBuilder;
import com.auralink.guide.model.GuideResultCodec;
import com.auralink.guide.provider.GuideProvider;
import com.auralink.guide.service.PaintingGuideGenerationGuard;
import com.auralink.guide.service.PaintingGuideLockRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auralink.catalog-guide", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CatalogGuideProperties.class)
public class CatalogGuideConfiguration {
   @Bean(name = "catalogGuideDataSource", destroyMethod = "close")
   HikariDataSource catalogGuideDataSource(CatalogGuideProperties properties) {
      CatalogGuideProperties.Datasource source = properties.getDatasource();
      if (source.getJdbcUrl() != null && source.getJdbcUrl().startsWith("jdbc:postgresql://")) {
         HikariConfig config = new HikariConfig();
         config.setPoolName("artlive-catalog-guide-postgres");
         config.setJdbcUrl(source.getJdbcUrl());
         config.setUsername(source.getUsername());
         config.setPassword(source.getPassword());
         config.setDriverClassName("org.postgresql.Driver");
         config.setMaximumPoolSize(source.getMaximumPoolSize());
         config.setMinimumIdle(source.getMinimumIdle());
         config.setConnectionTimeout(source.getConnectionTimeoutMs());
         config.setAutoCommit(true);
         config.setReadOnly(false);
         return new HikariDataSource(config);
      } else {
         throw new IllegalArgumentException("catalog-guide datasource must use an explicit PostgreSQL JDBC URL");
      }
   }

   @Bean(name = "catalogGuideJdbcTemplate")
   NamedParameterJdbcTemplate catalogGuideJdbcTemplate(@Qualifier("catalogGuideDataSource") DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
   }

   @Bean(name = "catalogGuideTransactionManager")
   PlatformTransactionManager catalogGuideTransactionManager(@Qualifier("catalogGuideDataSource") DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
   }

   @Bean
   PostgresCatalogGuideStore postgresCatalogGuideStore(@Qualifier("catalogGuideJdbcTemplate") NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
      return new PostgresCatalogGuideStore(jdbc, objectMapper);
   }

   @Bean
   PostgresPaintingGuideService postgresPaintingGuideService(
      CatalogReadStore catalogReadStore,
      PaintingGuideContextBuilder contextBuilder,
      KnowledgeContextBuilder knowledgeContextBuilder,
      GuideSourceHasher sourceHasher,
      GuideResultCodec resultCodec,
      GuideProvider guideProvider,
      PostgresCatalogGuideStore store,
      PaintingGuideLockRegistry lockRegistry,
      PaintingGuideGenerationGuard generationGuard,
      GuideProperties properties
   ) {
      return new PostgresPaintingGuideService(
         catalogReadStore, contextBuilder, knowledgeContextBuilder, sourceHasher, resultCodec, guideProvider, store, lockRegistry, generationGuard, properties
      );
   }
}

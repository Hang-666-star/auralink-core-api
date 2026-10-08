package com.auralink.catalogcritic;

import com.auralink.catalogread.CatalogMediaService;
import com.auralink.catalogread.CatalogReadStore;
import com.auralink.config.properties.CriticProperties;
import com.auralink.config.properties.MediaAssetProperties;
import com.auralink.critic.provider.CriticEvaluationProvider;
import com.auralink.critic.service.CriticResponseValidator;
import com.auralink.service.CurrentUserService;
import com.auralink.service.media.ImageContentValidator;
import com.auralink.service.media.MediaAssetStorageService;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auralink.catalog-critic", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CatalogCriticProperties.class)
public class CatalogCriticConfiguration {
   @Bean(name = "catalogCriticDataSource", destroyMethod = "close")
   HikariDataSource catalogCriticDataSource(CatalogCriticProperties properties) {
      CatalogCriticProperties.Datasource source = properties.getDatasource();
      if (source.getJdbcUrl() != null && source.getJdbcUrl().startsWith("jdbc:postgresql://")) {
         HikariConfig config = new HikariConfig();
         config.setPoolName("artlive-catalog-critic-postgres");
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
         throw new IllegalArgumentException("catalog-critic datasource must use an explicit PostgreSQL JDBC URL");
      }
   }

   @Bean(name = "catalogCriticJdbcTemplate")
   NamedParameterJdbcTemplate catalogCriticJdbcTemplate(@Qualifier("catalogCriticDataSource") DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
   }

   @Bean(name = "catalogCriticTransactionManager")
   PlatformTransactionManager catalogCriticTransactionManager(@Qualifier("catalogCriticDataSource") DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
   }

   @Bean
   PostgresCatalogCriticStore postgresCatalogCriticStore(@Qualifier("catalogCriticJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
      return new PostgresCatalogCriticStore(jdbc);
   }

   @Bean
   CatalogPrivateMediaService catalogPrivateMediaService(PostgresCatalogCriticStore store, CatalogCriticProperties properties) {
      return new CatalogPrivateMediaService(store, properties);
   }

   @Bean
   @ConditionalOnProperty(prefix = "auralink.catalog-critic", name = "private-media-upload-enabled", havingValue = "true")
   PostgresPrivateMediaUploadStore postgresPrivateMediaUploadStore(@Qualifier("catalogCriticJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
      return new PostgresPrivateMediaUploadStore(jdbc);
   }

   @Bean
   @ConditionalOnProperty(prefix = "auralink.catalog-critic", name = "private-media-upload-enabled", havingValue = "true")
   PostgresPrivateMediaUploadService postgresPrivateMediaUploadService(
      CurrentUserService users,
      MediaAssetProperties mediaProperties,
      MediaAssetStorageService managedStorage,
      PostgresPrivateMediaStorageService privateStorage,
      ImageContentValidator imageValidator,
      PostgresPrivateMediaUploadStore store
   ) {
      return new PostgresPrivateMediaUploadService(users, mediaProperties, managedStorage, privateStorage, imageValidator, store);
   }

   @Bean
   PostgresPaintingCritiqueService postgresPaintingCritiqueService(
      CurrentUserService users,
      CatalogReadStore catalogReadStore,
      CatalogMediaService catalogMediaService,
      CriticProperties criticProperties,
      CatalogCriticProperties catalogCriticProperties,
      PostgresCatalogCriticStore store,
      CriticEvaluationProvider provider,
      CriticResponseValidator validator,
      @Qualifier("criticTaskExecutor") ThreadPoolTaskExecutor executor,
      ObjectMapper mapper
   ) {
      return new PostgresPaintingCritiqueService(
         users, catalogReadStore, catalogMediaService, criticProperties, catalogCriticProperties, store, provider, validator, executor, mapper
      );
   }
}

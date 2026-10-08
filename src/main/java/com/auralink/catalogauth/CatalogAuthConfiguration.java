package com.auralink.catalogauth;

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
@ConditionalOnProperty(prefix = "auralink.catalog-auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CatalogAuthProperties.class)
public class CatalogAuthConfiguration {
   @Bean(name = "catalogAuthDataSource", destroyMethod = "close")
   HikariDataSource catalogAuthDataSource(CatalogAuthProperties properties) {
      CatalogAuthProperties.Datasource source = properties.getDatasource();
      if (source.getJdbcUrl() != null && source.getJdbcUrl().startsWith("jdbc:postgresql://")) {
         HikariConfig config = new HikariConfig();
         config.setPoolName("artlive-catalog-auth-postgres");
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
         throw new IllegalArgumentException("catalog-auth datasource must use an explicit PostgreSQL JDBC URL");
      }
   }

   @Bean(name = "catalogAuthJdbcTemplate")
   NamedParameterJdbcTemplate catalogAuthJdbcTemplate(@Qualifier("catalogAuthDataSource") DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
   }

   @Bean(name = "catalogAuthTransactionManager")
   PlatformTransactionManager catalogAuthTransactionManager(@Qualifier("catalogAuthDataSource") DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
   }

   @Bean
   CatalogAuthStore catalogAuthStore(@Qualifier("catalogAuthJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
      return new PostgresCatalogAuthStore(jdbc);
   }
}

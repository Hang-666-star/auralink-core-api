package com.auralink.catalogread;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@Profile(
   {
         "catalog-postgres-integration",
         "catalog-postgres-user-favorites-integration",
         "catalog-postgres-critic-assets-integration",
         "catalog-postgres-guide-integration"
   }
)
public class LocalCatalogReadIntegrationDataSourceConfiguration {
   @Bean(name = "applicationSqliteDataSource", destroyMethod = "close")
   @Primary
   public DataSource applicationSqliteDataSource(
      @Value("${spring.datasource.url}") String jdbcUrl, @Value("${spring.datasource.driver-class-name}") String driverClassName
   ) {
      if (!jdbcUrl.startsWith("jdbc:sqlite:")) {
         throw new IllegalStateException("local catalog PostgreSQL integration requires a disposable SQLite primary datasource");
      }

      HikariConfig config = new HikariConfig();
      config.setPoolName("catalogReadIntegrationSqlitePool");
      config.setJdbcUrl(jdbcUrl);
      config.setDriverClassName(driverClassName);
      config.setMaximumPoolSize(1);
      config.setMinimumIdle(1);
      config.setConnectionInitSql("PRAGMA foreign_keys=ON");
      return new HikariDataSource(config);
   }

   @Bean(name = "transactionManager")
   @Primary
   public PlatformTransactionManager applicationSqliteTransactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
   }
}

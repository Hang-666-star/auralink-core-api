package com.auralink.creation;

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
@Profile({"catalog-postgres-creation-integration", "catalog-postgres-production"})
public class CreationPostgresIntegrationDataSourceConfiguration {
   @Bean(name = "applicationCreationPostgresDataSource", destroyMethod = "close")
   @Primary
   DataSource applicationCreationPostgresDataSource(
      @Value("${spring.datasource.url}") String jdbcUrl,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driver-class-name}") String driverClassName
   ) {
      if (!jdbcUrl.startsWith("jdbc:postgresql://127.0.0.1:")) {
         throw new IllegalStateException("Creation PostgreSQL profile requires a loopback PostgreSQL datasource");
      }

      HikariConfig config = new HikariConfig();
      config.setPoolName("catalogCreationIntegrationPostgresPool");
      config.setJdbcUrl(jdbcUrl);
      config.setUsername(username);
      config.setPassword(password);
      config.setDriverClassName(driverClassName);
      config.setMaximumPoolSize(6);
      config.setMinimumIdle(0);
      config.setConnectionTimeout(2000L);
      return new HikariDataSource(config);
   }

   @Bean(name = "transactionManager")
   @Primary
   PlatformTransactionManager applicationCreationTransactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
   }
}

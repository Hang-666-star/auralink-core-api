package com.auralink.catalogauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.catalog-auth")
public class CatalogAuthProperties {
   private boolean enabled;
   private CatalogAuthProperties.Datasource datasource = new CatalogAuthProperties.Datasource();

   public boolean isEnabled() {
      return this.enabled;
   }

   public CatalogAuthProperties.Datasource getDatasource() {
      return this.datasource;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setDatasource(final CatalogAuthProperties.Datasource datasource) {
      this.datasource = datasource;
   }

   public static class Datasource {
      private String jdbcUrl;
      private String username;
      private String password;
      private int maximumPoolSize = 4;
      private int minimumIdle = 0;
      private long connectionTimeoutMs = 2000L;

      public String getJdbcUrl() {
         return this.jdbcUrl;
      }

      public String getUsername() {
         return this.username;
      }

      public String getPassword() {
         return this.password;
      }

      public int getMaximumPoolSize() {
         return this.maximumPoolSize;
      }

      public int getMinimumIdle() {
         return this.minimumIdle;
      }

      public long getConnectionTimeoutMs() {
         return this.connectionTimeoutMs;
      }

      public void setJdbcUrl(final String jdbcUrl) {
         this.jdbcUrl = jdbcUrl;
      }

      public void setUsername(final String username) {
         this.username = username;
      }

      public void setPassword(final String password) {
         this.password = password;
      }

      public void setMaximumPoolSize(final int maximumPoolSize) {
         this.maximumPoolSize = maximumPoolSize;
      }

      public void setMinimumIdle(final int minimumIdle) {
         this.minimumIdle = minimumIdle;
      }

      public void setConnectionTimeoutMs(final long connectionTimeoutMs) {
         this.connectionTimeoutMs = connectionTimeoutMs;
      }
   }
}

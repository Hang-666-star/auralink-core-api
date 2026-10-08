package com.auralink.catalogguide;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.catalog-guide")
public class CatalogGuideProperties {
   private boolean enabled;
   private CatalogGuideProperties.Datasource datasource = new CatalogGuideProperties.Datasource();

   public boolean isEnabled() {
      return this.enabled;
   }

   public CatalogGuideProperties.Datasource getDatasource() {
      return this.datasource;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setDatasource(final CatalogGuideProperties.Datasource datasource) {
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

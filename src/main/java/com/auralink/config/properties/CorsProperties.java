package com.auralink.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.cors")
public class CorsProperties {
   private List<String> allowedOrigins = List.of(
      "https://fanhualy.top",
      "http://fanhualy.top",
      "https://api.fanhualy.top",
      "http://api.fanhualy.top",
      "http://localhost:3000",
      "https://localhost:3000",
      "http://127.0.0.1:3100",
      "https://*.fanhualy.top",
      "http://*.fanhualy.top"
   );
   private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
   private List<String> allowedHeaders = List.of(
      "Origin", "Content-Type", "Accept", "Authorization", "X-Requested-With", "Cache-Control", "Range", "If-None-Match", "If-Modified-Since", "If-Range"
   );
   private List<String> exposedHeaders = List.of(
      "Authorization", "Content-Disposition", "Content-Type", "Content-Length", "Accept-Ranges", "Content-Range", "ETag", "Cache-Control", "Last-Modified"
   );
   private boolean allowCredentials = true;
   private long maxAgeSeconds = 3600L;

   public List<String> getAllowedOrigins() {
      return this.allowedOrigins;
   }

   public List<String> getAllowedMethods() {
      return this.allowedMethods;
   }

   public List<String> getAllowedHeaders() {
      return this.allowedHeaders;
   }

   public List<String> getExposedHeaders() {
      return this.exposedHeaders;
   }

   public boolean isAllowCredentials() {
      return this.allowCredentials;
   }

   public long getMaxAgeSeconds() {
      return this.maxAgeSeconds;
   }

   public void setAllowedOrigins(final List<String> allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
   }

   public void setAllowedMethods(final List<String> allowedMethods) {
      this.allowedMethods = allowedMethods;
   }

   public void setAllowedHeaders(final List<String> allowedHeaders) {
      this.allowedHeaders = allowedHeaders;
   }

   public void setExposedHeaders(final List<String> exposedHeaders) {
      this.exposedHeaders = exposedHeaders;
   }

   public void setAllowCredentials(final boolean allowCredentials) {
      this.allowCredentials = allowCredentials;
   }

   public void setMaxAgeSeconds(final long maxAgeSeconds) {
      this.maxAgeSeconds = maxAgeSeconds;
   }
}

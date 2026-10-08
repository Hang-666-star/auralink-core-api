package com.auralink.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.remote-fetch")
public class RemoteFetchProperties {
   private long maxBytes = 26214400L;
   private Duration connectTimeout = Duration.ofSeconds(5L);
   private Duration readTimeout = Duration.ofSeconds(30L);
   private Duration totalTimeout = Duration.ofMinutes(2L);
   private int maxRedirects = 3;

   public long getMaxBytes() {
      return this.maxBytes;
   }

   public Duration getConnectTimeout() {
      return this.connectTimeout;
   }

   public Duration getReadTimeout() {
      return this.readTimeout;
   }

   public Duration getTotalTimeout() {
      return this.totalTimeout;
   }

   public int getMaxRedirects() {
      return this.maxRedirects;
   }

   public void setMaxBytes(final long maxBytes) {
      this.maxBytes = maxBytes;
   }

   public void setConnectTimeout(final Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
   }

   public void setReadTimeout(final Duration readTimeout) {
      this.readTimeout = readTimeout;
   }

   public void setTotalTimeout(final Duration totalTimeout) {
      this.totalTimeout = totalTimeout;
   }

   public void setMaxRedirects(final int maxRedirects) {
      this.maxRedirects = maxRedirects;
   }
}

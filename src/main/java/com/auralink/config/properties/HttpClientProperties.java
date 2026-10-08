package com.auralink.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.http-client")
public class HttpClientProperties {
   private Duration connectTimeout = Duration.ofSeconds(15L);
   private Duration readTimeout = Duration.ofSeconds(120L);

   public Duration getConnectTimeout() {
      return this.connectTimeout;
   }

   public Duration getReadTimeout() {
      return this.readTimeout;
   }

   public void setConnectTimeout(final Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
   }

   public void setReadTimeout(final Duration readTimeout) {
      this.readTimeout = readTimeout;
   }
}

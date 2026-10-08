package com.auralink.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.jwt")
public class JwtProperties {
   private String secret;
   private long expiration = 604800000L;

   public String getSecret() {
      return this.secret;
   }

   public long getExpiration() {
      return this.expiration;
   }

   public void setSecret(final String secret) {
      this.secret = secret;
   }

   public void setExpiration(final long expiration) {
      this.expiration = expiration;
   }
}

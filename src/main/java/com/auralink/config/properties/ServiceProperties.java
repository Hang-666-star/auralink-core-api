package com.auralink.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.service")
public class ServiceProperties {
   private String vmmUrl = "http://localhost:5001";
   private String nonvmmUrl = "http://localhost:5002";

   public String getQwenServiceUrl() {
      return this.nonvmmUrl;
   }

   public String getVmmUrl() {
      return this.vmmUrl;
   }

   public String getNonvmmUrl() {
      return this.nonvmmUrl;
   }

   public void setVmmUrl(final String vmmUrl) {
      this.vmmUrl = vmmUrl;
   }

   public void setNonvmmUrl(final String nonvmmUrl) {
      this.nonvmmUrl = nonvmmUrl;
   }
}

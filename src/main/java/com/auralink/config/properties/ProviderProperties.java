package com.auralink.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.providers")
public class ProviderProperties {
   private ProviderProperties.Provider seedream = new ProviderProperties.Provider();
   private ProviderProperties.Provider qwen = new ProviderProperties.Provider();
   private ProviderProperties.Provider paintingMusic = new ProviderProperties.Provider();
   private ProviderProperties.Provider guide = new ProviderProperties.Provider();
   private ProviderProperties.Provider video = new ProviderProperties.Provider();

   public ProviderProperties.Provider getSeedream() {
      return this.seedream;
   }

   public ProviderProperties.Provider getQwen() {
      return this.qwen;
   }

   public ProviderProperties.Provider getPaintingMusic() {
      return this.paintingMusic;
   }

   public ProviderProperties.Provider getGuide() {
      return this.guide;
   }

   public ProviderProperties.Provider getVideo() {
      return this.video;
   }

   public void setSeedream(final ProviderProperties.Provider seedream) {
      this.seedream = seedream;
   }

   public void setQwen(final ProviderProperties.Provider qwen) {
      this.qwen = qwen;
   }

   public void setPaintingMusic(final ProviderProperties.Provider paintingMusic) {
      this.paintingMusic = paintingMusic;
   }

   public void setGuide(final ProviderProperties.Provider guide) {
      this.guide = guide;
   }

   public void setVideo(final ProviderProperties.Provider video) {
      this.video = video;
   }

   public static class Provider {
      private String apiKey = "";
      private String baseUrl = "";
      private String model = "";
      private String outputRoot = "";
      private boolean enabled;

      public String getApiKey() {
         return this.apiKey;
      }

      public String getBaseUrl() {
         return this.baseUrl;
      }

      public String getModel() {
         return this.model;
      }

      public String getOutputRoot() {
         return this.outputRoot;
      }

      public boolean isEnabled() {
         return this.enabled;
      }

      public void setApiKey(final String apiKey) {
         this.apiKey = apiKey;
      }

      public void setBaseUrl(final String baseUrl) {
         this.baseUrl = baseUrl;
      }

      public void setModel(final String model) {
         this.model = model;
      }

      public void setOutputRoot(final String outputRoot) {
         this.outputRoot = outputRoot;
      }

      public void setEnabled(final boolean enabled) {
         this.enabled = enabled;
      }
   }
}

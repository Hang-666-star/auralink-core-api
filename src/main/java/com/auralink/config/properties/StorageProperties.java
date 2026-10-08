package com.auralink.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auralink.storage")
public class StorageProperties {
   private String uploadDir = "./temp_uploads";
   private String audioDir = "../VMM-frontend/project/public/audios";
   private String legacyFrontendAudioDir = "../frontend/public/audios";

   public String getUploadDir() {
      return this.uploadDir;
   }

   public String getAudioDir() {
      return this.audioDir;
   }

   public String getLegacyFrontendAudioDir() {
      return this.legacyFrontendAudioDir;
   }

   public void setUploadDir(final String uploadDir) {
      this.uploadDir = uploadDir;
   }

   public void setAudioDir(final String audioDir) {
      this.audioDir = audioDir;
   }

   public void setLegacyFrontendAudioDir(final String legacyFrontendAudioDir) {
      this.legacyFrontendAudioDir = legacyFrontendAudioDir;
   }
}

package com.auralink.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.media-assets")
public class MediaAssetProperties {
   public static final long DEFAULT_MAX_UPLOAD_BYTES = 10485760L;
   public static final long DEFAULT_MAX_GENERATED_BYTES = 268435456L;
   public static final long DEFAULT_MAX_IMAGE_PIXELS = 40000000L;
   public static final long DEFAULT_PUBLIC_CACHE_SECONDS = 86400L;
   @NotBlank
   private String managedDir = "./temp_uploads/media-assets";
   @Min(1L)
   private long maxUploadBytes = 10485760L;
   @Min(1L)
   private long maxGeneratedBytes = 268435456L;
   @Min(1L)
   private long maxImagePixels = 40000000L;
   @Min(0L)
   private long publicCacheSeconds = 86400L;

   public String getManagedDir() {
      return this.managedDir;
   }

   public long getMaxUploadBytes() {
      return this.maxUploadBytes;
   }

   public long getMaxGeneratedBytes() {
      return this.maxGeneratedBytes;
   }

   public long getMaxImagePixels() {
      return this.maxImagePixels;
   }

   public long getPublicCacheSeconds() {
      return this.publicCacheSeconds;
   }

   public void setManagedDir(final String managedDir) {
      this.managedDir = managedDir;
   }

   public void setMaxUploadBytes(final long maxUploadBytes) {
      this.maxUploadBytes = maxUploadBytes;
   }

   public void setMaxGeneratedBytes(final long maxGeneratedBytes) {
      this.maxGeneratedBytes = maxGeneratedBytes;
   }

   public void setMaxImagePixels(final long maxImagePixels) {
      this.maxImagePixels = maxImagePixels;
   }

   public void setPublicCacheSeconds(final long publicCacheSeconds) {
      this.publicCacheSeconds = publicCacheSeconds;
   }
}

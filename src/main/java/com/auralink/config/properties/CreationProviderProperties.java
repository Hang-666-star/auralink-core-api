package com.auralink.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.creation-providers")
public class CreationProviderProperties {
   private boolean enabled = false;
   @NotNull
   private Path stagingDir = Path.of("/tmp/auralink-provider-staging");
   @Min(1L)
   private long maxImageInputBytes = 10485760L;
   @Min(1L)
   private long maxImageOutputBytes = 26214400L;
   @Min(1L)
   private long maxAudioOutputBytes = 268435456L;
   @Min(1L)
   private int maxTextChars = 20000;
   @NotNull
   private Duration connectTimeout = Duration.ofSeconds(5L);
   @NotNull
   private Duration qwenReadTimeout = Duration.ofMinutes(3L);
   @NotNull
   private Duration seedreamReadTimeout = Duration.ofMinutes(5L);
   @NotNull
   private Duration vmmReadTimeout = Duration.ofMinutes(10L);
   @Min(1L)
   private int maxConcurrentSeedream = 2;
   @Min(1L)
   private int maxConcurrentQwen = 4;
   @Min(1L)
   private int maxConcurrentVmm = 1;
   private String seedreamDefaultSize = "2K";
   private String seedreamOutputFormat = "png";
   private boolean seedreamWatermark = true;

   public boolean isEnabled() {
      return this.enabled;
   }

   public Path getStagingDir() {
      return this.stagingDir;
   }

   public long getMaxImageInputBytes() {
      return this.maxImageInputBytes;
   }

   public long getMaxImageOutputBytes() {
      return this.maxImageOutputBytes;
   }

   public long getMaxAudioOutputBytes() {
      return this.maxAudioOutputBytes;
   }

   public int getMaxTextChars() {
      return this.maxTextChars;
   }

   public Duration getConnectTimeout() {
      return this.connectTimeout;
   }

   public Duration getQwenReadTimeout() {
      return this.qwenReadTimeout;
   }

   public Duration getSeedreamReadTimeout() {
      return this.seedreamReadTimeout;
   }

   public Duration getVmmReadTimeout() {
      return this.vmmReadTimeout;
   }

   public int getMaxConcurrentSeedream() {
      return this.maxConcurrentSeedream;
   }

   public int getMaxConcurrentQwen() {
      return this.maxConcurrentQwen;
   }

   public int getMaxConcurrentVmm() {
      return this.maxConcurrentVmm;
   }

   public String getSeedreamDefaultSize() {
      return this.seedreamDefaultSize;
   }

   public String getSeedreamOutputFormat() {
      return this.seedreamOutputFormat;
   }

   public boolean isSeedreamWatermark() {
      return this.seedreamWatermark;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setStagingDir(final Path stagingDir) {
      this.stagingDir = stagingDir;
   }

   public void setMaxImageInputBytes(final long maxImageInputBytes) {
      this.maxImageInputBytes = maxImageInputBytes;
   }

   public void setMaxImageOutputBytes(final long maxImageOutputBytes) {
      this.maxImageOutputBytes = maxImageOutputBytes;
   }

   public void setMaxAudioOutputBytes(final long maxAudioOutputBytes) {
      this.maxAudioOutputBytes = maxAudioOutputBytes;
   }

   public void setMaxTextChars(final int maxTextChars) {
      this.maxTextChars = maxTextChars;
   }

   public void setConnectTimeout(final Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
   }

   public void setQwenReadTimeout(final Duration qwenReadTimeout) {
      this.qwenReadTimeout = qwenReadTimeout;
   }

   public void setSeedreamReadTimeout(final Duration seedreamReadTimeout) {
      this.seedreamReadTimeout = seedreamReadTimeout;
   }

   public void setVmmReadTimeout(final Duration vmmReadTimeout) {
      this.vmmReadTimeout = vmmReadTimeout;
   }

   public void setMaxConcurrentSeedream(final int maxConcurrentSeedream) {
      this.maxConcurrentSeedream = maxConcurrentSeedream;
   }

   public void setMaxConcurrentQwen(final int maxConcurrentQwen) {
      this.maxConcurrentQwen = maxConcurrentQwen;
   }

   public void setMaxConcurrentVmm(final int maxConcurrentVmm) {
      this.maxConcurrentVmm = maxConcurrentVmm;
   }

   public void setSeedreamDefaultSize(final String seedreamDefaultSize) {
      this.seedreamDefaultSize = seedreamDefaultSize;
   }

   public void setSeedreamOutputFormat(final String seedreamOutputFormat) {
      this.seedreamOutputFormat = seedreamOutputFormat;
   }

   public void setSeedreamWatermark(final boolean seedreamWatermark) {
      this.seedreamWatermark = seedreamWatermark;
   }
}

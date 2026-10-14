package com.auralink.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound only when explicitly enabled. No provider credentials or production storage defaults. */
@ConfigurationProperties("auralink.assistant")
public class AssistantProperties {
   private String attachmentRoot;
   private long maxUploadBytes = 10 * 1024 * 1024;
   private long maxImagePixels = 32_000_000;
   private Duration requestReplayWindow = Duration.ofDays(7);
   private int maxPendingPerSession = 8;
   public String getAttachmentRoot() { return attachmentRoot; }
   public void setAttachmentRoot(String value) { attachmentRoot = value; }
   public long getMaxUploadBytes() { return maxUploadBytes; }
   public void setMaxUploadBytes(long value) { maxUploadBytes = value; }
   public long getMaxImagePixels() { return maxImagePixels; }
   public void setMaxImagePixels(long value) { maxImagePixels = value; }
   public Duration getRequestReplayWindow() { return requestReplayWindow; }
   public void setRequestReplayWindow(Duration value) { requestReplayWindow = value; }
   public int getMaxPendingPerSession() { return maxPendingPerSession; }
   public void setMaxPendingPerSession(int value) { maxPendingPerSession = value; }
   void validate() {
      if (attachmentRoot == null || attachmentRoot.isBlank() || maxUploadBytes < 1 || maxUploadBytes > 10 * 1024 * 1024
         || maxImagePixels < 1 || maxImagePixels > 40_000_000 || requestReplayWindow == null
         || requestReplayWindow.isNegative() || requestReplayWindow.isZero() || maxPendingPerSession < 1 || maxPendingPerSession > 100) {
         throw new IllegalArgumentException("Explicit private Assistant storage and bounded Assistant limits are required");
      }
   }
}

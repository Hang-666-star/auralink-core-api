package com.auralink.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.workflows")
public class WorkflowProperties {
   private boolean enabled = false;
   @Min(1L)
   @Max(2L)
   private int schemaVersion = 2;
   @Min(1024L)
   private int maxGraphBytes = 65536;
   @Min(2L)
   private int maxNodes = 16;
   @Min(1L)
   private int maxEdges = 15;
   @Min(1L)
   private int maxNameChars = 120;
   @Min(0L)
   private int maxDescriptionChars = 2000;

   public boolean isEnabled() {
      return this.enabled;
   }

   public int getSchemaVersion() {
      return this.schemaVersion;
   }

   public int getMaxGraphBytes() {
      return this.maxGraphBytes;
   }

   public int getMaxNodes() {
      return this.maxNodes;
   }

   public int getMaxEdges() {
      return this.maxEdges;
   }

   public int getMaxNameChars() {
      return this.maxNameChars;
   }

   public int getMaxDescriptionChars() {
      return this.maxDescriptionChars;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setSchemaVersion(final int schemaVersion) {
      this.schemaVersion = schemaVersion;
   }

   public void setMaxGraphBytes(final int maxGraphBytes) {
      this.maxGraphBytes = maxGraphBytes;
   }

   public void setMaxNodes(final int maxNodes) {
      this.maxNodes = maxNodes;
   }

   public void setMaxEdges(final int maxEdges) {
      this.maxEdges = maxEdges;
   }

   public void setMaxNameChars(final int maxNameChars) {
      this.maxNameChars = maxNameChars;
   }

   public void setMaxDescriptionChars(final int maxDescriptionChars) {
      this.maxDescriptionChars = maxDescriptionChars;
   }
}

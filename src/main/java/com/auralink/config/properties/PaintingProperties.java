package com.auralink.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.paintings")
public class PaintingProperties {
   private String metadataCsvPath = "../frontend/public/data/paintings.csv";
   private String pictureDir = "./picture";
   private String imageBaseUrl = "https://api.auralinks.top/api/paintings/images";
   private Integer defaultLimit = 500;
   private Integer maxLimit = 2000;
   private boolean importEnabled = false;
   private boolean importFailOnError = true;
   @Min(1L)
   @Max(1000L)
   private int importBatchSize = 100;
   private ZoneId dailyZone = ZoneId.of("Asia/Shanghai");

   public String getMetadataCsvPath() {
      return this.metadataCsvPath;
   }

   public String getPictureDir() {
      return this.pictureDir;
   }

   public String getImageBaseUrl() {
      return this.imageBaseUrl;
   }

   public Integer getDefaultLimit() {
      return this.defaultLimit;
   }

   public Integer getMaxLimit() {
      return this.maxLimit;
   }

   public boolean isImportEnabled() {
      return this.importEnabled;
   }

   public boolean isImportFailOnError() {
      return this.importFailOnError;
   }

   public int getImportBatchSize() {
      return this.importBatchSize;
   }

   public ZoneId getDailyZone() {
      return this.dailyZone;
   }

   public void setMetadataCsvPath(final String metadataCsvPath) {
      this.metadataCsvPath = metadataCsvPath;
   }

   public void setPictureDir(final String pictureDir) {
      this.pictureDir = pictureDir;
   }

   public void setImageBaseUrl(final String imageBaseUrl) {
      this.imageBaseUrl = imageBaseUrl;
   }

   public void setDefaultLimit(final Integer defaultLimit) {
      this.defaultLimit = defaultLimit;
   }

   public void setMaxLimit(final Integer maxLimit) {
      this.maxLimit = maxLimit;
   }

   public void setImportEnabled(final boolean importEnabled) {
      this.importEnabled = importEnabled;
   }

   public void setImportFailOnError(final boolean importFailOnError) {
      this.importFailOnError = importFailOnError;
   }

   public void setImportBatchSize(final int importBatchSize) {
      this.importBatchSize = importBatchSize;
   }

   public void setDailyZone(final ZoneId dailyZone) {
      this.dailyZone = dailyZone;
   }
}

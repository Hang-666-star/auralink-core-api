package com.auralink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UploadResultRequest {
   @NotNull(message = "日志ID不能为空")
   private Long logId;
   @NotBlank(message = "内容类型不能为空")
   private String contentType;
   private String fileName;
   private String fileExtension;
   private String base64Data;
   private String remoteUrl;
   private String description;
   private String metadata;

   public boolean hasValidContent() {
      return this.base64Data != null && !this.base64Data.trim().isEmpty() || this.remoteUrl != null && !this.remoteUrl.trim().isEmpty();
   }

   public static UploadResultRequest.UploadResultRequestBuilder builder() {
      return new UploadResultRequest.UploadResultRequestBuilder();
   }

   public Long getLogId() {
      return this.logId;
   }

   public String getContentType() {
      return this.contentType;
   }

   public String getFileName() {
      return this.fileName;
   }

   public String getFileExtension() {
      return this.fileExtension;
   }

   public String getBase64Data() {
      return this.base64Data;
   }

   public String getRemoteUrl() {
      return this.remoteUrl;
   }

   public String getDescription() {
      return this.description;
   }

   public String getMetadata() {
      return this.metadata;
   }

   public void setLogId(final Long logId) {
      this.logId = logId;
   }

   public void setContentType(final String contentType) {
      this.contentType = contentType;
   }

   public void setFileName(final String fileName) {
      this.fileName = fileName;
   }

   public void setFileExtension(final String fileExtension) {
      this.fileExtension = fileExtension;
   }

   public void setBase64Data(final String base64Data) {
      this.base64Data = base64Data;
   }

   public void setRemoteUrl(final String remoteUrl) {
      this.remoteUrl = remoteUrl;
   }

   public void setDescription(final String description) {
      this.description = description;
   }

   public void setMetadata(final String metadata) {
      this.metadata = metadata;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof UploadResultRequest other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         Object this$logId = this.getLogId();
         Object other$logId = other.getLogId();
         if (this$logId == null ? other$logId == null : this$logId.equals(other$logId)) {
            Object this$contentType = this.getContentType();
            Object other$contentType = other.getContentType();
            if (this$contentType == null ? other$contentType == null : this$contentType.equals(other$contentType)) {
               Object this$fileName = this.getFileName();
               Object other$fileName = other.getFileName();
               if (this$fileName == null ? other$fileName == null : this$fileName.equals(other$fileName)) {
                  Object this$fileExtension = this.getFileExtension();
                  Object other$fileExtension = other.getFileExtension();
                  if (this$fileExtension == null ? other$fileExtension == null : this$fileExtension.equals(other$fileExtension)) {
                     Object this$base64Data = this.getBase64Data();
                     Object other$base64Data = other.getBase64Data();
                     if (this$base64Data == null ? other$base64Data == null : this$base64Data.equals(other$base64Data)) {
                        Object this$remoteUrl = this.getRemoteUrl();
                        Object other$remoteUrl = other.getRemoteUrl();
                        if (this$remoteUrl == null ? other$remoteUrl == null : this$remoteUrl.equals(other$remoteUrl)) {
                           Object this$description = this.getDescription();
                           Object other$description = other.getDescription();
                           if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                              Object this$metadata = this.getMetadata();
                              Object other$metadata = other.getMetadata();
                              return this$metadata == null ? other$metadata == null : this$metadata.equals(other$metadata);
                           } else {
                              return false;
                           }
                        } else {
                           return false;
                        }
                     } else {
                        return false;
                     }
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   protected boolean canEqual(final Object other) {
      return other instanceof UploadResultRequest;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $logId = this.getLogId();
      result = result * 59 + ($logId == null ? 43 : $logId.hashCode());
      Object $contentType = this.getContentType();
      result = result * 59 + ($contentType == null ? 43 : $contentType.hashCode());
      Object $fileName = this.getFileName();
      result = result * 59 + ($fileName == null ? 43 : $fileName.hashCode());
      Object $fileExtension = this.getFileExtension();
      result = result * 59 + ($fileExtension == null ? 43 : $fileExtension.hashCode());
      Object $base64Data = this.getBase64Data();
      result = result * 59 + ($base64Data == null ? 43 : $base64Data.hashCode());
      Object $remoteUrl = this.getRemoteUrl();
      result = result * 59 + ($remoteUrl == null ? 43 : $remoteUrl.hashCode());
      Object $description = this.getDescription();
      result = result * 59 + ($description == null ? 43 : $description.hashCode());
      Object $metadata = this.getMetadata();
      return result * 59 + ($metadata == null ? 43 : $metadata.hashCode());
   }

   @Override
   public String toString() {
      return "UploadResultRequest(logId="
         + this.getLogId()
         + ", contentType="
         + this.getContentType()
         + ", fileName="
         + this.getFileName()
         + ", fileExtension="
         + this.getFileExtension()
         + ", base64Data="
         + this.getBase64Data()
         + ", remoteUrl="
         + this.getRemoteUrl()
         + ", description="
         + this.getDescription()
         + ", metadata="
         + this.getMetadata()
         + ")";
   }

   public UploadResultRequest() {
   }

   public UploadResultRequest(
      final Long logId,
      final String contentType,
      final String fileName,
      final String fileExtension,
      final String base64Data,
      final String remoteUrl,
      final String description,
      final String metadata
   ) {
      this.logId = logId;
      this.contentType = contentType;
      this.fileName = fileName;
      this.fileExtension = fileExtension;
      this.base64Data = base64Data;
      this.remoteUrl = remoteUrl;
      this.description = description;
      this.metadata = metadata;
   }

   public static class UploadResultRequestBuilder {
      private Long logId;
      private String contentType;
      private String fileName;
      private String fileExtension;
      private String base64Data;
      private String remoteUrl;
      private String description;
      private String metadata;

      UploadResultRequestBuilder() {
      }

      public UploadResultRequest.UploadResultRequestBuilder logId(final Long logId) {
         this.logId = logId;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder contentType(final String contentType) {
         this.contentType = contentType;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder fileName(final String fileName) {
         this.fileName = fileName;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder fileExtension(final String fileExtension) {
         this.fileExtension = fileExtension;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder base64Data(final String base64Data) {
         this.base64Data = base64Data;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder remoteUrl(final String remoteUrl) {
         this.remoteUrl = remoteUrl;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder description(final String description) {
         this.description = description;
         return this;
      }

      public UploadResultRequest.UploadResultRequestBuilder metadata(final String metadata) {
         this.metadata = metadata;
         return this;
      }

      public UploadResultRequest build() {
         return new UploadResultRequest(
            this.logId, this.contentType, this.fileName, this.fileExtension, this.base64Data, this.remoteUrl, this.description, this.metadata
         );
      }

      @Override
      public String toString() {
         return "UploadResultRequest.UploadResultRequestBuilder(logId="
            + this.logId
            + ", contentType="
            + this.contentType
            + ", fileName="
            + this.fileName
            + ", fileExtension="
            + this.fileExtension
            + ", base64Data="
            + this.base64Data
            + ", remoteUrl="
            + this.remoteUrl
            + ", description="
            + this.description
            + ", metadata="
            + this.metadata
            + ")";
      }
   }
}

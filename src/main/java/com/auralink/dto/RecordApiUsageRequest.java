package com.auralink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RecordApiUsageRequest {
   @NotBlank(message = "任务类型不能为空")
   private String taskType;
   @NotBlank(message = "API来源不能为空")
   private String apiSource;
   private String apiProvider;
   private String inputData;
   private String outputData;
   private String imageUrl;
   private String resultUrl;
   private String description;
   private String modelSize;
   private Boolean useFastGenerate;
   private Integer duration;
   private Long processingTimeMs;
   @NotNull(message = "成功状态不能为空")
   private Boolean success;
   private String errorMessage;
   private String metadata;

   public static RecordApiUsageRequest.RecordApiUsageRequestBuilder builder() {
      return new RecordApiUsageRequest.RecordApiUsageRequestBuilder();
   }

   public String getTaskType() {
      return this.taskType;
   }

   public String getApiSource() {
      return this.apiSource;
   }

   public String getApiProvider() {
      return this.apiProvider;
   }

   public String getInputData() {
      return this.inputData;
   }

   public String getOutputData() {
      return this.outputData;
   }

   public String getImageUrl() {
      return this.imageUrl;
   }

   public String getResultUrl() {
      return this.resultUrl;
   }

   public String getDescription() {
      return this.description;
   }

   public String getModelSize() {
      return this.modelSize;
   }

   public Boolean getUseFastGenerate() {
      return this.useFastGenerate;
   }

   public Integer getDuration() {
      return this.duration;
   }

   public Long getProcessingTimeMs() {
      return this.processingTimeMs;
   }

   public Boolean getSuccess() {
      return this.success;
   }

   public String getErrorMessage() {
      return this.errorMessage;
   }

   public String getMetadata() {
      return this.metadata;
   }

   public void setTaskType(final String taskType) {
      this.taskType = taskType;
   }

   public void setApiSource(final String apiSource) {
      this.apiSource = apiSource;
   }

   public void setApiProvider(final String apiProvider) {
      this.apiProvider = apiProvider;
   }

   public void setInputData(final String inputData) {
      this.inputData = inputData;
   }

   public void setOutputData(final String outputData) {
      this.outputData = outputData;
   }

   public void setImageUrl(final String imageUrl) {
      this.imageUrl = imageUrl;
   }

   public void setResultUrl(final String resultUrl) {
      this.resultUrl = resultUrl;
   }

   public void setDescription(final String description) {
      this.description = description;
   }

   public void setModelSize(final String modelSize) {
      this.modelSize = modelSize;
   }

   public void setUseFastGenerate(final Boolean useFastGenerate) {
      this.useFastGenerate = useFastGenerate;
   }

   public void setDuration(final Integer duration) {
      this.duration = duration;
   }

   public void setProcessingTimeMs(final Long processingTimeMs) {
      this.processingTimeMs = processingTimeMs;
   }

   public void setSuccess(final Boolean success) {
      this.success = success;
   }

   public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public void setMetadata(final String metadata) {
      this.metadata = metadata;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof RecordApiUsageRequest other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         Object this$useFastGenerate = this.getUseFastGenerate();
         Object other$useFastGenerate = other.getUseFastGenerate();
         if (this$useFastGenerate == null ? other$useFastGenerate == null : this$useFastGenerate.equals(other$useFastGenerate)) {
            Object this$duration = this.getDuration();
            Object other$duration = other.getDuration();
            if (this$duration == null ? other$duration == null : this$duration.equals(other$duration)) {
               Object this$processingTimeMs = this.getProcessingTimeMs();
               Object other$processingTimeMs = other.getProcessingTimeMs();
               if (this$processingTimeMs == null ? other$processingTimeMs == null : this$processingTimeMs.equals(other$processingTimeMs)) {
                  Object this$success = this.getSuccess();
                  Object other$success = other.getSuccess();
                  if (this$success == null ? other$success == null : this$success.equals(other$success)) {
                     Object this$taskType = this.getTaskType();
                     Object other$taskType = other.getTaskType();
                     if (this$taskType == null ? other$taskType == null : this$taskType.equals(other$taskType)) {
                        Object this$apiSource = this.getApiSource();
                        Object other$apiSource = other.getApiSource();
                        if (this$apiSource == null ? other$apiSource == null : this$apiSource.equals(other$apiSource)) {
                           Object this$apiProvider = this.getApiProvider();
                           Object other$apiProvider = other.getApiProvider();
                           if (this$apiProvider == null ? other$apiProvider == null : this$apiProvider.equals(other$apiProvider)) {
                              Object this$inputData = this.getInputData();
                              Object other$inputData = other.getInputData();
                              if (this$inputData == null ? other$inputData == null : this$inputData.equals(other$inputData)) {
                                 Object this$outputData = this.getOutputData();
                                 Object other$outputData = other.getOutputData();
                                 if (this$outputData == null ? other$outputData == null : this$outputData.equals(other$outputData)) {
                                    Object this$imageUrl = this.getImageUrl();
                                    Object other$imageUrl = other.getImageUrl();
                                    if (this$imageUrl == null ? other$imageUrl == null : this$imageUrl.equals(other$imageUrl)) {
                                       Object this$resultUrl = this.getResultUrl();
                                       Object other$resultUrl = other.getResultUrl();
                                       if (this$resultUrl == null ? other$resultUrl == null : this$resultUrl.equals(other$resultUrl)) {
                                          Object this$description = this.getDescription();
                                          Object other$description = other.getDescription();
                                          if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                                             Object this$modelSize = this.getModelSize();
                                             Object other$modelSize = other.getModelSize();
                                             if (this$modelSize == null ? other$modelSize == null : this$modelSize.equals(other$modelSize)) {
                                                Object this$errorMessage = this.getErrorMessage();
                                                Object other$errorMessage = other.getErrorMessage();
                                                if (this$errorMessage == null ? other$errorMessage == null : this$errorMessage.equals(other$errorMessage)) {
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
      return other instanceof RecordApiUsageRequest;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $useFastGenerate = this.getUseFastGenerate();
      result = result * 59 + ($useFastGenerate == null ? 43 : $useFastGenerate.hashCode());
      Object $duration = this.getDuration();
      result = result * 59 + ($duration == null ? 43 : $duration.hashCode());
      Object $processingTimeMs = this.getProcessingTimeMs();
      result = result * 59 + ($processingTimeMs == null ? 43 : $processingTimeMs.hashCode());
      Object $success = this.getSuccess();
      result = result * 59 + ($success == null ? 43 : $success.hashCode());
      Object $taskType = this.getTaskType();
      result = result * 59 + ($taskType == null ? 43 : $taskType.hashCode());
      Object $apiSource = this.getApiSource();
      result = result * 59 + ($apiSource == null ? 43 : $apiSource.hashCode());
      Object $apiProvider = this.getApiProvider();
      result = result * 59 + ($apiProvider == null ? 43 : $apiProvider.hashCode());
      Object $inputData = this.getInputData();
      result = result * 59 + ($inputData == null ? 43 : $inputData.hashCode());
      Object $outputData = this.getOutputData();
      result = result * 59 + ($outputData == null ? 43 : $outputData.hashCode());
      Object $imageUrl = this.getImageUrl();
      result = result * 59 + ($imageUrl == null ? 43 : $imageUrl.hashCode());
      Object $resultUrl = this.getResultUrl();
      result = result * 59 + ($resultUrl == null ? 43 : $resultUrl.hashCode());
      Object $description = this.getDescription();
      result = result * 59 + ($description == null ? 43 : $description.hashCode());
      Object $modelSize = this.getModelSize();
      result = result * 59 + ($modelSize == null ? 43 : $modelSize.hashCode());
      Object $errorMessage = this.getErrorMessage();
      result = result * 59 + ($errorMessage == null ? 43 : $errorMessage.hashCode());
      Object $metadata = this.getMetadata();
      return result * 59 + ($metadata == null ? 43 : $metadata.hashCode());
   }

   @Override
   public String toString() {
      return "RecordApiUsageRequest(taskType="
         + this.getTaskType()
         + ", apiSource="
         + this.getApiSource()
         + ", apiProvider="
         + this.getApiProvider()
         + ", inputData="
         + this.getInputData()
         + ", outputData="
         + this.getOutputData()
         + ", imageUrl="
         + this.getImageUrl()
         + ", resultUrl="
         + this.getResultUrl()
         + ", description="
         + this.getDescription()
         + ", modelSize="
         + this.getModelSize()
         + ", useFastGenerate="
         + this.getUseFastGenerate()
         + ", duration="
         + this.getDuration()
         + ", processingTimeMs="
         + this.getProcessingTimeMs()
         + ", success="
         + this.getSuccess()
         + ", errorMessage="
         + this.getErrorMessage()
         + ", metadata="
         + this.getMetadata()
         + ")";
   }

   public RecordApiUsageRequest() {
   }

   public RecordApiUsageRequest(
      final String taskType,
      final String apiSource,
      final String apiProvider,
      final String inputData,
      final String outputData,
      final String imageUrl,
      final String resultUrl,
      final String description,
      final String modelSize,
      final Boolean useFastGenerate,
      final Integer duration,
      final Long processingTimeMs,
      final Boolean success,
      final String errorMessage,
      final String metadata
   ) {
      this.taskType = taskType;
      this.apiSource = apiSource;
      this.apiProvider = apiProvider;
      this.inputData = inputData;
      this.outputData = outputData;
      this.imageUrl = imageUrl;
      this.resultUrl = resultUrl;
      this.description = description;
      this.modelSize = modelSize;
      this.useFastGenerate = useFastGenerate;
      this.duration = duration;
      this.processingTimeMs = processingTimeMs;
      this.success = success;
      this.errorMessage = errorMessage;
      this.metadata = metadata;
   }

   public static class RecordApiUsageRequestBuilder {
      private String taskType;
      private String apiSource;
      private String apiProvider;
      private String inputData;
      private String outputData;
      private String imageUrl;
      private String resultUrl;
      private String description;
      private String modelSize;
      private Boolean useFastGenerate;
      private Integer duration;
      private Long processingTimeMs;
      private Boolean success;
      private String errorMessage;
      private String metadata;

      RecordApiUsageRequestBuilder() {
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder taskType(final String taskType) {
         this.taskType = taskType;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder apiSource(final String apiSource) {
         this.apiSource = apiSource;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder apiProvider(final String apiProvider) {
         this.apiProvider = apiProvider;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder inputData(final String inputData) {
         this.inputData = inputData;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder outputData(final String outputData) {
         this.outputData = outputData;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder imageUrl(final String imageUrl) {
         this.imageUrl = imageUrl;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder resultUrl(final String resultUrl) {
         this.resultUrl = resultUrl;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder description(final String description) {
         this.description = description;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder modelSize(final String modelSize) {
         this.modelSize = modelSize;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder useFastGenerate(final Boolean useFastGenerate) {
         this.useFastGenerate = useFastGenerate;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder duration(final Integer duration) {
         this.duration = duration;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder processingTimeMs(final Long processingTimeMs) {
         this.processingTimeMs = processingTimeMs;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder success(final Boolean success) {
         this.success = success;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder errorMessage(final String errorMessage) {
         this.errorMessage = errorMessage;
         return this;
      }

      public RecordApiUsageRequest.RecordApiUsageRequestBuilder metadata(final String metadata) {
         this.metadata = metadata;
         return this;
      }

      public RecordApiUsageRequest build() {
         return new RecordApiUsageRequest(
            this.taskType,
            this.apiSource,
            this.apiProvider,
            this.inputData,
            this.outputData,
            this.imageUrl,
            this.resultUrl,
            this.description,
            this.modelSize,
            this.useFastGenerate,
            this.duration,
            this.processingTimeMs,
            this.success,
            this.errorMessage,
            this.metadata
         );
      }

      @Override
      public String toString() {
         return "RecordApiUsageRequest.RecordApiUsageRequestBuilder(taskType="
            + this.taskType
            + ", apiSource="
            + this.apiSource
            + ", apiProvider="
            + this.apiProvider
            + ", inputData="
            + this.inputData
            + ", outputData="
            + this.outputData
            + ", imageUrl="
            + this.imageUrl
            + ", resultUrl="
            + this.resultUrl
            + ", description="
            + this.description
            + ", modelSize="
            + this.modelSize
            + ", useFastGenerate="
            + this.useFastGenerate
            + ", duration="
            + this.duration
            + ", processingTimeMs="
            + this.processingTimeMs
            + ", success="
            + this.success
            + ", errorMessage="
            + this.errorMessage
            + ", metadata="
            + this.metadata
            + ")";
      }
   }
}

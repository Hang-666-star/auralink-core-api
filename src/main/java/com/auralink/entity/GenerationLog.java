package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "generation_logs")
public class GenerationLog {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;
   @ManyToOne
   @JoinColumn(name = "user_id", nullable = false)
   private User user;
   @Column(nullable = false)
   private String taskType;
   @Column(nullable = false)
   private String apiSource;
   @Column
   private String apiProvider;
   @Column(columnDefinition = "TEXT")
   private String inputData;
   @Column(columnDefinition = "TEXT")
   private String outputData;
   @Column(length = 1024)
   private String imageUrl;
   @Column(length = 1024)
   private String resultUrl;
   @Column(length = 1024)
   private String description;
   @Column(nullable = false)
   private String modelSize;
   @Column(nullable = false)
   private boolean useFastGenerate;
   @Column
   private Integer duration;
   @Column
   private Long processingTimeMs;
   @Column(nullable = false)
   private boolean success;
   @Column(length = 1024)
   private String errorMessage;
   @Column(columnDefinition = "TEXT")
   private String metadata;
   @Column(nullable = false)
   private LocalDateTime createdAt;

   @PrePersist
   protected void onCreate() {
      this.createdAt = LocalDateTime.now();
   }

   public String getType() {
      return this.taskType;
   }

   public void setType(String type) {
      this.taskType = type;
   }

   public static GenerationLog.GenerationLogBuilder builder() {
      return new GenerationLog.GenerationLogBuilder();
   }

   public Long getId() {
      return this.id;
   }

   public User getUser() {
      return this.user;
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

   public boolean isUseFastGenerate() {
      return this.useFastGenerate;
   }

   public Integer getDuration() {
      return this.duration;
   }

   public Long getProcessingTimeMs() {
      return this.processingTimeMs;
   }

   public boolean isSuccess() {
      return this.success;
   }

   public String getErrorMessage() {
      return this.errorMessage;
   }

   public String getMetadata() {
      return this.metadata;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public void setId(final Long id) {
      this.id = id;
   }

   public void setUser(final User user) {
      this.user = user;
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

   public void setUseFastGenerate(final boolean useFastGenerate) {
      this.useFastGenerate = useFastGenerate;
   }

   public void setDuration(final Integer duration) {
      this.duration = duration;
   }

   public void setProcessingTimeMs(final Long processingTimeMs) {
      this.processingTimeMs = processingTimeMs;
   }

   public void setSuccess(final boolean success) {
      this.success = success;
   }

   public void setErrorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public void setMetadata(final String metadata) {
      this.metadata = metadata;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof GenerationLog other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         if (this.isUseFastGenerate() != other.isUseFastGenerate()) {
            return false;
         }

         if (this.isSuccess() != other.isSuccess()) {
            return false;
         }

         Object this$id = this.getId();
         Object other$id = other.getId();
         if (this$id == null ? other$id == null : this$id.equals(other$id)) {
            Object this$duration = this.getDuration();
            Object other$duration = other.getDuration();
            if (this$duration == null ? other$duration == null : this$duration.equals(other$duration)) {
               Object this$processingTimeMs = this.getProcessingTimeMs();
               Object other$processingTimeMs = other.getProcessingTimeMs();
               if (this$processingTimeMs == null ? other$processingTimeMs == null : this$processingTimeMs.equals(other$processingTimeMs)) {
                  Object this$user = this.getUser();
                  Object other$user = other.getUser();
                  if (this$user == null ? other$user == null : this$user.equals(other$user)) {
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
                                                   if (this$metadata == null ? other$metadata == null : this$metadata.equals(other$metadata)) {
                                                      Object this$createdAt = this.getCreatedAt();
                                                      Object other$createdAt = other.getCreatedAt();
                                                      return this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt);
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
         } else {
            return false;
         }
      }
   }

   protected boolean canEqual(final Object other) {
      return other instanceof GenerationLog;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      result = result * 59 + (this.isUseFastGenerate() ? 79 : 97);
      result = result * 59 + (this.isSuccess() ? 79 : 97);
      Object $id = this.getId();
      result = result * 59 + ($id == null ? 43 : $id.hashCode());
      Object $duration = this.getDuration();
      result = result * 59 + ($duration == null ? 43 : $duration.hashCode());
      Object $processingTimeMs = this.getProcessingTimeMs();
      result = result * 59 + ($processingTimeMs == null ? 43 : $processingTimeMs.hashCode());
      Object $user = this.getUser();
      result = result * 59 + ($user == null ? 43 : $user.hashCode());
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
      result = result * 59 + ($metadata == null ? 43 : $metadata.hashCode());
      Object $createdAt = this.getCreatedAt();
      return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
   }

   @Override
   public String toString() {
      return "GenerationLog(id="
         + this.getId()
         + ", user="
         + this.getUser()
         + ", taskType="
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
         + this.isUseFastGenerate()
         + ", duration="
         + this.getDuration()
         + ", processingTimeMs="
         + this.getProcessingTimeMs()
         + ", success="
         + this.isSuccess()
         + ", errorMessage="
         + this.getErrorMessage()
         + ", metadata="
         + this.getMetadata()
         + ", createdAt="
         + this.getCreatedAt()
         + ")";
   }

   public GenerationLog() {
   }

   public GenerationLog(
      final Long id,
      final User user,
      final String taskType,
      final String apiSource,
      final String apiProvider,
      final String inputData,
      final String outputData,
      final String imageUrl,
      final String resultUrl,
      final String description,
      final String modelSize,
      final boolean useFastGenerate,
      final Integer duration,
      final Long processingTimeMs,
      final boolean success,
      final String errorMessage,
      final String metadata,
      final LocalDateTime createdAt
   ) {
      this.id = id;
      this.user = user;
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
      this.createdAt = createdAt;
   }

   public static class GenerationLogBuilder {
      private Long id;
      private User user;
      private String taskType;
      private String apiSource;
      private String apiProvider;
      private String inputData;
      private String outputData;
      private String imageUrl;
      private String resultUrl;
      private String description;
      private String modelSize;
      private boolean useFastGenerate;
      private Integer duration;
      private Long processingTimeMs;
      private boolean success;
      private String errorMessage;
      private String metadata;
      private LocalDateTime createdAt;

      GenerationLogBuilder() {
      }

      public GenerationLog.GenerationLogBuilder id(final Long id) {
         this.id = id;
         return this;
      }

      public GenerationLog.GenerationLogBuilder user(final User user) {
         this.user = user;
         return this;
      }

      public GenerationLog.GenerationLogBuilder taskType(final String taskType) {
         this.taskType = taskType;
         return this;
      }

      public GenerationLog.GenerationLogBuilder apiSource(final String apiSource) {
         this.apiSource = apiSource;
         return this;
      }

      public GenerationLog.GenerationLogBuilder apiProvider(final String apiProvider) {
         this.apiProvider = apiProvider;
         return this;
      }

      public GenerationLog.GenerationLogBuilder inputData(final String inputData) {
         this.inputData = inputData;
         return this;
      }

      public GenerationLog.GenerationLogBuilder outputData(final String outputData) {
         this.outputData = outputData;
         return this;
      }

      public GenerationLog.GenerationLogBuilder imageUrl(final String imageUrl) {
         this.imageUrl = imageUrl;
         return this;
      }

      public GenerationLog.GenerationLogBuilder resultUrl(final String resultUrl) {
         this.resultUrl = resultUrl;
         return this;
      }

      public GenerationLog.GenerationLogBuilder description(final String description) {
         this.description = description;
         return this;
      }

      public GenerationLog.GenerationLogBuilder modelSize(final String modelSize) {
         this.modelSize = modelSize;
         return this;
      }

      public GenerationLog.GenerationLogBuilder useFastGenerate(final boolean useFastGenerate) {
         this.useFastGenerate = useFastGenerate;
         return this;
      }

      public GenerationLog.GenerationLogBuilder duration(final Integer duration) {
         this.duration = duration;
         return this;
      }

      public GenerationLog.GenerationLogBuilder processingTimeMs(final Long processingTimeMs) {
         this.processingTimeMs = processingTimeMs;
         return this;
      }

      public GenerationLog.GenerationLogBuilder success(final boolean success) {
         this.success = success;
         return this;
      }

      public GenerationLog.GenerationLogBuilder errorMessage(final String errorMessage) {
         this.errorMessage = errorMessage;
         return this;
      }

      public GenerationLog.GenerationLogBuilder metadata(final String metadata) {
         this.metadata = metadata;
         return this;
      }

      public GenerationLog.GenerationLogBuilder createdAt(final LocalDateTime createdAt) {
         this.createdAt = createdAt;
         return this;
      }

      public GenerationLog build() {
         return new GenerationLog(
            this.id,
            this.user,
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
            this.metadata,
            this.createdAt
         );
      }

      @Override
      public String toString() {
         return "GenerationLog.GenerationLogBuilder(id="
            + this.id
            + ", user="
            + this.user
            + ", taskType="
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
            + ", createdAt="
            + this.createdAt
            + ")";
      }
   }
}

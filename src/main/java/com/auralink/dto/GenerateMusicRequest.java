package com.auralink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class GenerateMusicRequest {
   @NotBlank(message = "图片URL不能为空")
   private String imageUrl;
   @NotBlank(message = "模型大小不能为空")
   private String modelSize;
   private Boolean useFastGenerate = false;
   @Min(value = 10L, message = "音乐时长最短为10秒")
   @Max(value = 120L, message = "音乐时长最长为120秒")
   private Integer duration = 30;
   private String textDescription;

   public static GenerateMusicRequest.GenerateMusicRequestBuilder builder() {
      return new GenerateMusicRequest.GenerateMusicRequestBuilder();
   }

   public String getImageUrl() {
      return this.imageUrl;
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

   public String getTextDescription() {
      return this.textDescription;
   }

   public void setImageUrl(final String imageUrl) {
      this.imageUrl = imageUrl;
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

   public void setTextDescription(final String textDescription) {
      this.textDescription = textDescription;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof GenerateMusicRequest other)) {
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
               Object this$imageUrl = this.getImageUrl();
               Object other$imageUrl = other.getImageUrl();
               if (this$imageUrl == null ? other$imageUrl == null : this$imageUrl.equals(other$imageUrl)) {
                  Object this$modelSize = this.getModelSize();
                  Object other$modelSize = other.getModelSize();
                  if (this$modelSize == null ? other$modelSize == null : this$modelSize.equals(other$modelSize)) {
                     Object this$textDescription = this.getTextDescription();
                     Object other$textDescription = other.getTextDescription();
                     return this$textDescription == null ? other$textDescription == null : this$textDescription.equals(other$textDescription);
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
      return other instanceof GenerateMusicRequest;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $useFastGenerate = this.getUseFastGenerate();
      result = result * 59 + ($useFastGenerate == null ? 43 : $useFastGenerate.hashCode());
      Object $duration = this.getDuration();
      result = result * 59 + ($duration == null ? 43 : $duration.hashCode());
      Object $imageUrl = this.getImageUrl();
      result = result * 59 + ($imageUrl == null ? 43 : $imageUrl.hashCode());
      Object $modelSize = this.getModelSize();
      result = result * 59 + ($modelSize == null ? 43 : $modelSize.hashCode());
      Object $textDescription = this.getTextDescription();
      return result * 59 + ($textDescription == null ? 43 : $textDescription.hashCode());
   }

   @Override
   public String toString() {
      return "GenerateMusicRequest(imageUrl="
         + this.getImageUrl()
         + ", modelSize="
         + this.getModelSize()
         + ", useFastGenerate="
         + this.getUseFastGenerate()
         + ", duration="
         + this.getDuration()
         + ", textDescription="
         + this.getTextDescription()
         + ")";
   }

   public GenerateMusicRequest() {
   }

   public GenerateMusicRequest(
      final String imageUrl, final String modelSize, final Boolean useFastGenerate, final Integer duration, final String textDescription
   ) {
      this.imageUrl = imageUrl;
      this.modelSize = modelSize;
      this.useFastGenerate = useFastGenerate;
      this.duration = duration;
      this.textDescription = textDescription;
   }

   public static class GenerateMusicRequestBuilder {
      private String imageUrl;
      private String modelSize;
      private Boolean useFastGenerate;
      private Integer duration;
      private String textDescription;

      GenerateMusicRequestBuilder() {
      }

      public GenerateMusicRequest.GenerateMusicRequestBuilder imageUrl(final String imageUrl) {
         this.imageUrl = imageUrl;
         return this;
      }

      public GenerateMusicRequest.GenerateMusicRequestBuilder modelSize(final String modelSize) {
         this.modelSize = modelSize;
         return this;
      }

      public GenerateMusicRequest.GenerateMusicRequestBuilder useFastGenerate(final Boolean useFastGenerate) {
         this.useFastGenerate = useFastGenerate;
         return this;
      }

      public GenerateMusicRequest.GenerateMusicRequestBuilder duration(final Integer duration) {
         this.duration = duration;
         return this;
      }

      public GenerateMusicRequest.GenerateMusicRequestBuilder textDescription(final String textDescription) {
         this.textDescription = textDescription;
         return this;
      }

      public GenerateMusicRequest build() {
         return new GenerateMusicRequest(this.imageUrl, this.modelSize, this.useFastGenerate, this.duration, this.textDescription);
      }

      @Override
      public String toString() {
         return "GenerateMusicRequest.GenerateMusicRequestBuilder(imageUrl="
            + this.imageUrl
            + ", modelSize="
            + this.modelSize
            + ", useFastGenerate="
            + this.useFastGenerate
            + ", duration="
            + this.duration
            + ", textDescription="
            + this.textDescription
            + ")";
      }
   }
}

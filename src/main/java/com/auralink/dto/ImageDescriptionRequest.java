package com.auralink.dto;

import jakarta.validation.constraints.NotBlank;

public class ImageDescriptionRequest {
   @NotBlank(message = "图片URL不能为空")
   private String imageUrl;

   public static ImageDescriptionRequest.ImageDescriptionRequestBuilder builder() {
      return new ImageDescriptionRequest.ImageDescriptionRequestBuilder();
   }

   public String getImageUrl() {
      return this.imageUrl;
   }

   public void setImageUrl(final String imageUrl) {
      this.imageUrl = imageUrl;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof ImageDescriptionRequest other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         Object this$imageUrl = this.getImageUrl();
         Object other$imageUrl = other.getImageUrl();
         return this$imageUrl == null ? other$imageUrl == null : this$imageUrl.equals(other$imageUrl);
      }
   }

   protected boolean canEqual(final Object other) {
      return other instanceof ImageDescriptionRequest;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $imageUrl = this.getImageUrl();
      return result * 59 + ($imageUrl == null ? 43 : $imageUrl.hashCode());
   }

   @Override
   public String toString() {
      return "ImageDescriptionRequest(imageUrl=" + this.getImageUrl() + ")";
   }

   public ImageDescriptionRequest() {
   }

   public ImageDescriptionRequest(final String imageUrl) {
      this.imageUrl = imageUrl;
   }

   public static class ImageDescriptionRequestBuilder {
      private String imageUrl;

      ImageDescriptionRequestBuilder() {
      }

      public ImageDescriptionRequest.ImageDescriptionRequestBuilder imageUrl(final String imageUrl) {
         this.imageUrl = imageUrl;
         return this;
      }

      public ImageDescriptionRequest build() {
         return new ImageDescriptionRequest(this.imageUrl);
      }

      @Override
      public String toString() {
         return "ImageDescriptionRequest.ImageDescriptionRequestBuilder(imageUrl=" + this.imageUrl + ")";
      }
   }
}

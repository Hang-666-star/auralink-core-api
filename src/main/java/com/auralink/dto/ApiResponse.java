package com.auralink.dto;

public class ApiResponse<T> {
   private boolean success;
   private String message;
   private T data;

   public static <T> ApiResponse<T> success(T data) {
      return ApiResponse.<T>builder().success(true).data(data).build();
   }

   public static <T> ApiResponse<T> success(String message, T data) {
      return ApiResponse.<T>builder().success(true).message(message).data(data).build();
   }

   public static <T> ApiResponse<T> error(String message) {
      return ApiResponse.<T>builder().success(false).message(message).build();
   }

   public static <T> ApiResponse.ApiResponseBuilder<T> builder() {
      return new ApiResponse.ApiResponseBuilder<>();
   }

   public boolean isSuccess() {
      return this.success;
   }

   public String getMessage() {
      return this.message;
   }

   public T getData() {
      return this.data;
   }

   public void setSuccess(final boolean success) {
      this.success = success;
   }

   public void setMessage(final String message) {
      this.message = message;
   }

   public void setData(final T data) {
      this.data = data;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof ApiResponse<?> other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else if (this.isSuccess() != other.isSuccess()) {
         return false;
      } else {
         Object this$message = this.getMessage();
         Object other$message = other.getMessage();
         if (this$message == null ? other$message == null : this$message.equals(other$message)) {
            Object this$data = this.getData();
            Object other$data = other.getData();
            return this$data == null ? other$data == null : this$data.equals(other$data);
         } else {
            return false;
         }
      }
   }

   protected boolean canEqual(final Object other) {
      return other instanceof ApiResponse;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      result = result * 59 + (this.isSuccess() ? 79 : 97);
      Object $message = this.getMessage();
      result = result * 59 + ($message == null ? 43 : $message.hashCode());
      Object $data = this.getData();
      return result * 59 + ($data == null ? 43 : $data.hashCode());
   }

   @Override
   public String toString() {
      return "ApiResponse(success=" + this.isSuccess() + ", message=" + this.getMessage() + ", data=" + this.getData() + ")";
   }

   public ApiResponse() {
   }

   public ApiResponse(final boolean success, final String message, final T data) {
      this.success = success;
      this.message = message;
      this.data = data;
   }

   public static class ApiResponseBuilder<T> {
      private boolean success;
      private String message;
      private T data;

      ApiResponseBuilder() {
      }

      public ApiResponse.ApiResponseBuilder<T> success(final boolean success) {
         this.success = success;
         return this;
      }

      public ApiResponse.ApiResponseBuilder<T> message(final String message) {
         this.message = message;
         return this;
      }

      public ApiResponse.ApiResponseBuilder<T> data(final T data) {
         this.data = data;
         return this;
      }

      public ApiResponse<T> build() {
         return new ApiResponse<>(this.success, this.message, this.data);
      }

      @Override
      public String toString() {
         return "ApiResponse.ApiResponseBuilder(success=" + this.success + ", message=" + this.message + ", data=" + this.data + ")";
      }
   }
}

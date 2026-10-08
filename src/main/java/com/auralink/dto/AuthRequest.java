package com.auralink.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthRequest {
   @NotBlank(message = "用户名不能为空")
   private String username;
   @NotBlank(message = "密码不能为空")
   private String password;

   public static AuthRequest.AuthRequestBuilder builder() {
      return new AuthRequest.AuthRequestBuilder();
   }

   public String getUsername() {
      return this.username;
   }

   public String getPassword() {
      return this.password;
   }

   public void setUsername(final String username) {
      this.username = username;
   }

   public void setPassword(final String password) {
      this.password = password;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof AuthRequest other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else {
         Object this$username = this.getUsername();
         Object other$username = other.getUsername();
         if (this$username == null ? other$username == null : this$username.equals(other$username)) {
            Object this$password = this.getPassword();
            Object other$password = other.getPassword();
            return this$password == null ? other$password == null : this$password.equals(other$password);
         } else {
            return false;
         }
      }
   }

   protected boolean canEqual(final Object other) {
      return other instanceof AuthRequest;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $username = this.getUsername();
      result = result * 59 + ($username == null ? 43 : $username.hashCode());
      Object $password = this.getPassword();
      return result * 59 + ($password == null ? 43 : $password.hashCode());
   }

   @Override
   public String toString() {
      return "AuthRequest(username=" + this.getUsername() + ", password=" + this.getPassword() + ")";
   }

   public AuthRequest() {
   }

   public AuthRequest(final String username, final String password) {
      this.username = username;
      this.password = password;
   }

   public static class AuthRequestBuilder {
      private String username;
      private String password;

      AuthRequestBuilder() {
      }

      public AuthRequest.AuthRequestBuilder username(final String username) {
         this.username = username;
         return this;
      }

      public AuthRequest.AuthRequestBuilder password(final String password) {
         this.password = password;
         return this;
      }

      public AuthRequest build() {
         return new AuthRequest(this.username, this.password);
      }

      @Override
      public String toString() {
         return "AuthRequest.AuthRequestBuilder(username=" + this.username + ", password=" + this.password + ")";
      }
   }
}

package com.auralink.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
   @NotBlank(message = "用户名不能为空")
   @Size(min = 4, max = 50, message = "用户名长度必须在4-50个字符之间")
   private String username;
   @NotBlank(message = "密码不能为空")
   @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
   private String password;
   @NotBlank(message = "全名不能为空")
   @Size(max = 100, message = "全名长度不能超过100个字符")
   private String fullName;
   @NotBlank(message = "邮箱不能为空")
   @Email(message = "邮箱格式不正确")
   private String email;

   public static RegisterRequest.RegisterRequestBuilder builder() {
      return new RegisterRequest.RegisterRequestBuilder();
   }

   public String getUsername() {
      return this.username;
   }

   public String getPassword() {
      return this.password;
   }

   public String getFullName() {
      return this.fullName;
   }

   public String getEmail() {
      return this.email;
   }

   public void setUsername(final String username) {
      this.username = username;
   }

   public void setPassword(final String password) {
      this.password = password;
   }

   public void setFullName(final String fullName) {
      this.fullName = fullName;
   }

   public void setEmail(final String email) {
      this.email = email;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof RegisterRequest other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         Object this$username = this.getUsername();
         Object other$username = other.getUsername();
         if (this$username == null ? other$username == null : this$username.equals(other$username)) {
            Object this$password = this.getPassword();
            Object other$password = other.getPassword();
            if (this$password == null ? other$password == null : this$password.equals(other$password)) {
               Object this$fullName = this.getFullName();
               Object other$fullName = other.getFullName();
               if (this$fullName == null ? other$fullName == null : this$fullName.equals(other$fullName)) {
                  Object this$email = this.getEmail();
                  Object other$email = other.getEmail();
                  return this$email == null ? other$email == null : this$email.equals(other$email);
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
      return other instanceof RegisterRequest;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $username = this.getUsername();
      result = result * 59 + ($username == null ? 43 : $username.hashCode());
      Object $password = this.getPassword();
      result = result * 59 + ($password == null ? 43 : $password.hashCode());
      Object $fullName = this.getFullName();
      result = result * 59 + ($fullName == null ? 43 : $fullName.hashCode());
      Object $email = this.getEmail();
      return result * 59 + ($email == null ? 43 : $email.hashCode());
   }

   @Override
   public String toString() {
      return "RegisterRequest(username="
         + this.getUsername()
         + ", password="
         + this.getPassword()
         + ", fullName="
         + this.getFullName()
         + ", email="
         + this.getEmail()
         + ")";
   }

   public RegisterRequest() {
   }

   public RegisterRequest(final String username, final String password, final String fullName, final String email) {
      this.username = username;
      this.password = password;
      this.fullName = fullName;
      this.email = email;
   }

   public static class RegisterRequestBuilder {
      private String username;
      private String password;
      private String fullName;
      private String email;

      RegisterRequestBuilder() {
      }

      public RegisterRequest.RegisterRequestBuilder username(final String username) {
         this.username = username;
         return this;
      }

      public RegisterRequest.RegisterRequestBuilder password(final String password) {
         this.password = password;
         return this;
      }

      public RegisterRequest.RegisterRequestBuilder fullName(final String fullName) {
         this.fullName = fullName;
         return this;
      }

      public RegisterRequest.RegisterRequestBuilder email(final String email) {
         this.email = email;
         return this;
      }

      public RegisterRequest build() {
         return new RegisterRequest(this.username, this.password, this.fullName, this.email);
      }

      @Override
      public String toString() {
         return "RegisterRequest.RegisterRequestBuilder(username="
            + this.username
            + ", password="
            + this.password
            + ", fullName="
            + this.fullName
            + ", email="
            + this.email
            + ")";
      }
   }
}

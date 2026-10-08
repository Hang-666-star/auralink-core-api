package com.auralink.dto;

public class AuthResponse {
   private String token;
   private Long userId;
   private String username;
   private String fullName;

   public static AuthResponse.AuthResponseBuilder builder() {
      return new AuthResponse.AuthResponseBuilder();
   }

   public String getToken() {
      return this.token;
   }

   public Long getUserId() {
      return this.userId;
   }

   public String getUsername() {
      return this.username;
   }

   public String getFullName() {
      return this.fullName;
   }

   public void setToken(final String token) {
      this.token = token;
   }

   public void setUserId(final Long userId) {
      this.userId = userId;
   }

   public void setUsername(final String username) {
      this.username = username;
   }

   public void setFullName(final String fullName) {
      this.fullName = fullName;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof AuthResponse other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         Object this$userId = this.getUserId();
         Object other$userId = other.getUserId();
         if (this$userId == null ? other$userId == null : this$userId.equals(other$userId)) {
            Object this$token = this.getToken();
            Object other$token = other.getToken();
            if (this$token == null ? other$token == null : this$token.equals(other$token)) {
               Object this$username = this.getUsername();
               Object other$username = other.getUsername();
               if (this$username == null ? other$username == null : this$username.equals(other$username)) {
                  Object this$fullName = this.getFullName();
                  Object other$fullName = other.getFullName();
                  return this$fullName == null ? other$fullName == null : this$fullName.equals(other$fullName);
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
      return other instanceof AuthResponse;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $userId = this.getUserId();
      result = result * 59 + ($userId == null ? 43 : $userId.hashCode());
      Object $token = this.getToken();
      result = result * 59 + ($token == null ? 43 : $token.hashCode());
      Object $username = this.getUsername();
      result = result * 59 + ($username == null ? 43 : $username.hashCode());
      Object $fullName = this.getFullName();
      return result * 59 + ($fullName == null ? 43 : $fullName.hashCode());
   }

   @Override
   public String toString() {
      return "AuthResponse(token="
         + this.getToken()
         + ", userId="
         + this.getUserId()
         + ", username="
         + this.getUsername()
         + ", fullName="
         + this.getFullName()
         + ")";
   }

   public AuthResponse() {
   }

   public AuthResponse(final String token, final Long userId, final String username, final String fullName) {
      this.token = token;
      this.userId = userId;
      this.username = username;
      this.fullName = fullName;
   }

   public static class AuthResponseBuilder {
      private String token;
      private Long userId;
      private String username;
      private String fullName;

      AuthResponseBuilder() {
      }

      public AuthResponse.AuthResponseBuilder token(final String token) {
         this.token = token;
         return this;
      }

      public AuthResponse.AuthResponseBuilder userId(final Long userId) {
         this.userId = userId;
         return this;
      }

      public AuthResponse.AuthResponseBuilder username(final String username) {
         this.username = username;
         return this;
      }

      public AuthResponse.AuthResponseBuilder fullName(final String fullName) {
         this.fullName = fullName;
         return this;
      }

      public AuthResponse build() {
         return new AuthResponse(this.token, this.userId, this.username, this.fullName);
      }

      @Override
      public String toString() {
         return "AuthResponse.AuthResponseBuilder(token="
            + this.token
            + ", userId="
            + this.userId
            + ", username="
            + this.username
            + ", fullName="
            + this.fullName
            + ")";
      }
   }
}

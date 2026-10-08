package com.auralink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
public class User implements UserDetails {
   private static final long serialVersionUID = 1L;
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;
   @Column(nullable = false, unique = true)
   private String username;
   @Column(nullable = false)
   private String password;
   @Column(nullable = false)
   private String fullName;
   @Column(nullable = false, unique = true)
   private String email;
   @Column(nullable = false)
   private boolean enabled;
   @Column(nullable = false)
   private boolean accountNonExpired;
   @Column(nullable = false)
   private boolean accountNonLocked;
   @Column(nullable = false)
   private boolean credentialsNonExpired;
   @Column(nullable = false)
   private String role;
   private LocalDateTime createdAt;
   private LocalDateTime updatedAt;

   @PrePersist
   protected void onCreate() {
      this.createdAt = LocalDateTime.now();
      this.updatedAt = LocalDateTime.now();
   }

   @PreUpdate
   protected void onUpdate() {
      this.updatedAt = LocalDateTime.now();
   }

   public Collection<? extends GrantedAuthority> getAuthorities() {
      return Collections.singletonList(new SimpleGrantedAuthority(this.role));
   }

   public boolean isAccountNonExpired() {
      return this.accountNonExpired;
   }

   public boolean isAccountNonLocked() {
      return this.accountNonLocked;
   }

   public boolean isCredentialsNonExpired() {
      return this.credentialsNonExpired;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   private static boolean $default$enabled() {
      return true;
   }

   private static boolean $default$accountNonExpired() {
      return true;
   }

   private static boolean $default$accountNonLocked() {
      return true;
   }

   private static boolean $default$credentialsNonExpired() {
      return true;
   }

   private static String $default$role() {
      return "ROLE_USER";
   }

   public static User.UserBuilder builder() {
      return new User.UserBuilder();
   }

   public Long getId() {
      return this.id;
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

   public String getRole() {
      return this.role;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public LocalDateTime getUpdatedAt() {
      return this.updatedAt;
   }

   public void setId(final Long id) {
      this.id = id;
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

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setAccountNonExpired(final boolean accountNonExpired) {
      this.accountNonExpired = accountNonExpired;
   }

   public void setAccountNonLocked(final boolean accountNonLocked) {
      this.accountNonLocked = accountNonLocked;
   }

   public void setCredentialsNonExpired(final boolean credentialsNonExpired) {
      this.credentialsNonExpired = credentialsNonExpired;
   }

   public void setRole(final String role) {
      this.role = role;
   }

   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public void setUpdatedAt(final LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
   }

   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof User other)) {
         return false;
      } else {
         if (!other.canEqual(this)) {
            return false;
         }

         if (this.isEnabled() != other.isEnabled()) {
            return false;
         }

         if (this.isAccountNonExpired() != other.isAccountNonExpired()) {
            return false;
         }

         if (this.isAccountNonLocked() != other.isAccountNonLocked()) {
            return false;
         }

         if (this.isCredentialsNonExpired() != other.isCredentialsNonExpired()) {
            return false;
         }

         Object this$id = this.getId();
         Object other$id = other.getId();
         if (this$id == null ? other$id == null : this$id.equals(other$id)) {
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
                     if (this$email == null ? other$email == null : this$email.equals(other$email)) {
                        Object this$role = this.getRole();
                        Object other$role = other.getRole();
                        if (this$role == null ? other$role == null : this$role.equals(other$role)) {
                           Object this$createdAt = this.getCreatedAt();
                           Object other$createdAt = other.getCreatedAt();
                           if (this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt)) {
                              Object this$updatedAt = this.getUpdatedAt();
                              Object other$updatedAt = other.getUpdatedAt();
                              return this$updatedAt == null ? other$updatedAt == null : this$updatedAt.equals(other$updatedAt);
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
      return other instanceof User;
   }

   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      result = result * 59 + (this.isEnabled() ? 79 : 97);
      result = result * 59 + (this.isAccountNonExpired() ? 79 : 97);
      result = result * 59 + (this.isAccountNonLocked() ? 79 : 97);
      result = result * 59 + (this.isCredentialsNonExpired() ? 79 : 97);
      Object $id = this.getId();
      result = result * 59 + ($id == null ? 43 : $id.hashCode());
      Object $username = this.getUsername();
      result = result * 59 + ($username == null ? 43 : $username.hashCode());
      Object $password = this.getPassword();
      result = result * 59 + ($password == null ? 43 : $password.hashCode());
      Object $fullName = this.getFullName();
      result = result * 59 + ($fullName == null ? 43 : $fullName.hashCode());
      Object $email = this.getEmail();
      result = result * 59 + ($email == null ? 43 : $email.hashCode());
      Object $role = this.getRole();
      result = result * 59 + ($role == null ? 43 : $role.hashCode());
      Object $createdAt = this.getCreatedAt();
      result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
      Object $updatedAt = this.getUpdatedAt();
      return result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
   }

   @Override
   public String toString() {
      return "User(id="
         + this.getId()
         + ", username="
         + this.getUsername()
         + ", password="
         + this.getPassword()
         + ", fullName="
         + this.getFullName()
         + ", email="
         + this.getEmail()
         + ", enabled="
         + this.isEnabled()
         + ", accountNonExpired="
         + this.isAccountNonExpired()
         + ", accountNonLocked="
         + this.isAccountNonLocked()
         + ", credentialsNonExpired="
         + this.isCredentialsNonExpired()
         + ", role="
         + this.getRole()
         + ", createdAt="
         + this.getCreatedAt()
         + ", updatedAt="
         + this.getUpdatedAt()
         + ")";
   }

   public User() {
      this.enabled = $default$enabled();
      this.accountNonExpired = $default$accountNonExpired();
      this.accountNonLocked = $default$accountNonLocked();
      this.credentialsNonExpired = $default$credentialsNonExpired();
      this.role = $default$role();
   }

   public User(
      final Long id,
      final String username,
      final String password,
      final String fullName,
      final String email,
      final boolean enabled,
      final boolean accountNonExpired,
      final boolean accountNonLocked,
      final boolean credentialsNonExpired,
      final String role,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt
   ) {
      this.id = id;
      this.username = username;
      this.password = password;
      this.fullName = fullName;
      this.email = email;
      this.enabled = enabled;
      this.accountNonExpired = accountNonExpired;
      this.accountNonLocked = accountNonLocked;
      this.credentialsNonExpired = credentialsNonExpired;
      this.role = role;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
   }

   public static class UserBuilder {
      private Long id;
      private String username;
      private String password;
      private String fullName;
      private String email;
      private boolean enabled$set;
      private boolean enabled$value;
      private boolean accountNonExpired$set;
      private boolean accountNonExpired$value;
      private boolean accountNonLocked$set;
      private boolean accountNonLocked$value;
      private boolean credentialsNonExpired$set;
      private boolean credentialsNonExpired$value;
      private boolean role$set;
      private String role$value;
      private LocalDateTime createdAt;
      private LocalDateTime updatedAt;

      UserBuilder() {
      }

      public User.UserBuilder id(final Long id) {
         this.id = id;
         return this;
      }

      public User.UserBuilder username(final String username) {
         this.username = username;
         return this;
      }

      public User.UserBuilder password(final String password) {
         this.password = password;
         return this;
      }

      public User.UserBuilder fullName(final String fullName) {
         this.fullName = fullName;
         return this;
      }

      public User.UserBuilder email(final String email) {
         this.email = email;
         return this;
      }

      public User.UserBuilder enabled(final boolean enabled) {
         this.enabled$value = enabled;
         this.enabled$set = true;
         return this;
      }

      public User.UserBuilder accountNonExpired(final boolean accountNonExpired) {
         this.accountNonExpired$value = accountNonExpired;
         this.accountNonExpired$set = true;
         return this;
      }

      public User.UserBuilder accountNonLocked(final boolean accountNonLocked) {
         this.accountNonLocked$value = accountNonLocked;
         this.accountNonLocked$set = true;
         return this;
      }

      public User.UserBuilder credentialsNonExpired(final boolean credentialsNonExpired) {
         this.credentialsNonExpired$value = credentialsNonExpired;
         this.credentialsNonExpired$set = true;
         return this;
      }

      public User.UserBuilder role(final String role) {
         this.role$value = role;
         this.role$set = true;
         return this;
      }

      public User.UserBuilder createdAt(final LocalDateTime createdAt) {
         this.createdAt = createdAt;
         return this;
      }

      public User.UserBuilder updatedAt(final LocalDateTime updatedAt) {
         this.updatedAt = updatedAt;
         return this;
      }

      public User build() {
         boolean enabled$value = this.enabled$value;
         if (!this.enabled$set) {
            enabled$value = User.$default$enabled();
         }

         boolean accountNonExpired$value = this.accountNonExpired$value;
         if (!this.accountNonExpired$set) {
            accountNonExpired$value = User.$default$accountNonExpired();
         }

         boolean accountNonLocked$value = this.accountNonLocked$value;
         if (!this.accountNonLocked$set) {
            accountNonLocked$value = User.$default$accountNonLocked();
         }

         boolean credentialsNonExpired$value = this.credentialsNonExpired$value;
         if (!this.credentialsNonExpired$set) {
            credentialsNonExpired$value = User.$default$credentialsNonExpired();
         }

         String role$value = this.role$value;
         if (!this.role$set) {
            role$value = User.$default$role();
         }

         return new User(
            this.id,
            this.username,
            this.password,
            this.fullName,
            this.email,
            enabled$value,
            accountNonExpired$value,
            accountNonLocked$value,
            credentialsNonExpired$value,
            role$value,
            this.createdAt,
            this.updatedAt
         );
      }

      @Override
      public String toString() {
         return "User.UserBuilder(id="
            + this.id
            + ", username="
            + this.username
            + ", password="
            + this.password
            + ", fullName="
            + this.fullName
            + ", email="
            + this.email
            + ", enabled$value="
            + this.enabled$value
            + ", accountNonExpired$value="
            + this.accountNonExpired$value
            + ", accountNonLocked$value="
            + this.accountNonLocked$value
            + ", credentialsNonExpired$value="
            + this.credentialsNonExpired$value
            + ", role$value="
            + this.role$value
            + ", createdAt="
            + this.createdAt
            + ", updatedAt="
            + this.updatedAt
            + ")";
      }
   }
}

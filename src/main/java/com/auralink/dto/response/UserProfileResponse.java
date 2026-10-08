package com.auralink.dto.response;

import com.auralink.entity.User;
import java.time.LocalDateTime;

public record UserProfileResponse(Long id, String username, String fullName, String email, LocalDateTime createdAt, LocalDateTime updatedAt) {
   public static UserProfileResponse from(User user) {
      return new UserProfileResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
   }
}

package com.auralink.dto.response;

import com.auralink.entity.User;

public record UserSummaryResponse(Long id, String username, String fullName) {
   public static UserSummaryResponse from(User user) {
      return user == null ? null : new UserSummaryResponse(user.getId(), user.getUsername(), user.getFullName());
   }
}

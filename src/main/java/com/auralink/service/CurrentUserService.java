package com.auralink.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.catalogauth.CatalogAuthStore;
import com.auralink.entity.User;
import com.auralink.repository.UserRepository;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
   private final UserRepository userRepository;
   private final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider;

   public Optional<User> findCurrentUser() {
      return this.findCurrentUser(SecurityContextHolder.getContext().getAuthentication());
   }

   public Optional<User> findCurrentUser(Authentication authentication) {
      if (!this.isAuthenticatedUser(authentication)) {
         return Optional.empty();
      } else {
         String username = authentication.getName();
         if (username != null && !username.isBlank()) {
            CatalogAuthStore catalogAuthStore = (CatalogAuthStore)this.catalogAuthStoreProvider.getIfAvailable();
            return catalogAuthStore == null ? this.userRepository.findByUsername(username) : catalogAuthStore.findByUsername(username);
         } else {
            return Optional.empty();
         }
      }
   }

   public User requireCurrentUser() {
      return this.findCurrentUser().orElseThrow(CurrentUserService::unauthorized);
   }

   public User requireCurrentUser(Authentication authentication) {
      return this.findCurrentUser(authentication).orElseThrow(CurrentUserService::unauthorized);
   }

   private boolean isAuthenticatedUser(Authentication authentication) {
      return authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
   }

   private static ApiV1Exception unauthorized() {
      return new ApiV1Exception(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "需要身份验证");
   }

   public CurrentUserService(final UserRepository userRepository, final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider) {
      this.userRepository = userRepository;
      this.catalogAuthStoreProvider = catalogAuthStoreProvider;
   }
}

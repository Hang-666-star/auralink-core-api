package com.auralink.service;

import com.auralink.catalogauth.CatalogAuthStore;
import com.auralink.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
   private final UserRepository userRepository;
   private final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider;

   public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
      CatalogAuthStore catalogAuthStore = (CatalogAuthStore)this.catalogAuthStoreProvider.getIfAvailable();
      return (catalogAuthStore == null ? this.userRepository.findByUsername(username) : catalogAuthStore.findByUsername(username))
         .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
   }

   public CustomUserDetailsService(final UserRepository userRepository, final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider) {
      this.userRepository = userRepository;
      this.catalogAuthStoreProvider = catalogAuthStoreProvider;
   }
}

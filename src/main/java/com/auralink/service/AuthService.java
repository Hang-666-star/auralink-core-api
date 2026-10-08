package com.auralink.service;

import com.auralink.catalogauth.CatalogAuthStore;
import com.auralink.dto.AuthRequest;
import com.auralink.dto.AuthResponse;
import com.auralink.dto.RegisterRequest;
import com.auralink.entity.User;
import com.auralink.exception.UserAlreadyExistsException;
import com.auralink.repository.UserRepository;
import com.auralink.security.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
   private static final Logger log = LoggerFactory.getLogger(AuthService.class);
   private final UserRepository userRepository;
   private final PasswordEncoder passwordEncoder;
   private final JwtTokenProvider jwtTokenProvider;
   private final AuthenticationManager authenticationManager;
   private final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider;

   @Transactional
   public AuthResponse register(RegisterRequest request) {
      log.info("注册新用户: {}", request.getUsername());
      CatalogAuthStore catalogAuthStore = (CatalogAuthStore)this.catalogAuthStoreProvider.getIfAvailable();
      if (catalogAuthStore != null) {
         User user = catalogAuthStore.register(
            request.getUsername(), this.passwordEncoder.encode(request.getPassword()), request.getFullName(), request.getEmail()
         );
         return this.responseFor(user);
      }

      if (this.userRepository.existsByUsername(request.getUsername())) {
         throw new UserAlreadyExistsException("用户名已存在");
      }

      if (this.userRepository.existsByEmail(request.getEmail())) {
         throw new UserAlreadyExistsException("邮箱已被注册");
      }

      User user = User.builder()
         .username(request.getUsername())
         .password(this.passwordEncoder.encode(request.getPassword()))
         .fullName(request.getFullName())
         .email(request.getEmail())
         .role("ROLE_USER")
         .build();

      try {
         this.userRepository.saveAndFlush(user);
      } catch (DataAccessException exception) {
         log.error("用户注册持久化失败 stage=user_insert_flush type={}", exception.getClass().getSimpleName(), exception);
         throw exception;
      }

      return this.responseFor(user);
   }

   public AuthResponse login(AuthRequest request) {
      log.info("用户登录: {}", request.getUsername());
      this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
      CatalogAuthStore catalogAuthStore = (CatalogAuthStore)this.catalogAuthStoreProvider.getIfAvailable();
      User user = (catalogAuthStore == null
            ? this.userRepository.findByUsername(request.getUsername())
            : catalogAuthStore.findByUsername(request.getUsername()))
         .orElseThrow(() -> new IllegalStateException("用户验证通过但未找到用户"));
      return this.responseFor(user);
   }

   private AuthResponse responseFor(User user) {
      String token = this.jwtTokenProvider.generateToken(user);
      return AuthResponse.builder().token(token).userId(user.getId()).username(user.getUsername()).fullName(user.getFullName()).build();
   }

   public AuthService(
      final UserRepository userRepository,
      final PasswordEncoder passwordEncoder,
      final JwtTokenProvider jwtTokenProvider,
      final AuthenticationManager authenticationManager,
      final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider
   ) {
      this.userRepository = userRepository;
      this.passwordEncoder = passwordEncoder;
      this.jwtTokenProvider = jwtTokenProvider;
      this.authenticationManager = authenticationManager;
      this.catalogAuthStoreProvider = catalogAuthStoreProvider;
   }
}

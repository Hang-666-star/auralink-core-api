package com.auralink.controller;

import com.auralink.dto.ApiResponse;
import com.auralink.dto.AuthRequest;
import com.auralink.dto.AuthResponse;
import com.auralink.dto.RegisterRequest;
import com.auralink.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
   private static final Logger log = LoggerFactory.getLogger(AuthController.class);
   private final AuthService authService;

   @PostMapping("/register")
   public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
      log.info("收到注册请求: {}", request.getUsername());
      AuthResponse response = this.authService.register(request);
      return ResponseEntity.ok(ApiResponse.success("用户注册成功", response));
   }

   @PostMapping("/login")
   public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
      log.info("收到登录请求: {}", request.getUsername());
      AuthResponse response = this.authService.login(request);
      return ResponseEntity.ok(ApiResponse.success("登录成功", response));
   }

   public AuthController(final AuthService authService) {
      this.authService = authService;
   }
}

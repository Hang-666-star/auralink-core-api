package com.auralink.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
   private final ObjectMapper objectMapper;

   public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
      response.setStatus(401);
      response.setContentType("application/json");
      JwtAuthenticationEntryPoint.ErrorResponse errorResponse = new JwtAuthenticationEntryPoint.ErrorResponse(false, "授权失败: " + authException.getMessage());

      try (PrintWriter writer = response.getWriter()) {
         writer.write(this.objectMapper.writeValueAsString(errorResponse));
         writer.flush();
      }
   }

   public JwtAuthenticationEntryPoint(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
   }

   private static class ErrorResponse {
      private final boolean success;
      private final String message;

      public ErrorResponse(boolean success, String message) {
         this.success = success;
         this.message = message;
      }

      public boolean isSuccess() {
         return this.success;
      }

      public String getMessage() {
         return this.message;
      }
   }
}

package com.auralink.api.v1.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ApiV1AuthenticationEntryPoint implements AuthenticationEntryPoint {
   private static final String CORRELATION_HEADER = "X-Correlation-ID";
   private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
   private final ObjectMapper objectMapper;

   public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) throws IOException {
      String correlationId = this.correlationId(request);
      ApiV1ErrorResponse body = new ApiV1ErrorResponse(
         Instant.now(),
         HttpStatus.UNAUTHORIZED.value(),
         ApiErrorCode.UNAUTHORIZED.name(),
         "需要身份验证",
         request.getRequestURI(),
         correlationId,
         Map.of(),
         List.of()
      );
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType("application/json");
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.setHeader("X-Correlation-ID", correlationId);
      this.objectMapper.writeValue(response.getOutputStream(), body);
   }

   private String correlationId(HttpServletRequest request) {
      String supplied = request.getHeader("X-Correlation-ID");
      return supplied != null && SAFE_CORRELATION_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
   }

   public ApiV1AuthenticationEntryPoint(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
   }
}

package com.auralink.security.jwt;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1ErrorResponse;
import com.auralink.api.v1.error.ApiV1Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
   private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
   private final JwtTokenProvider jwtTokenProvider;
   private final UserDetailsService userDetailsService;
   private final ObjectMapper objectMapper;

   public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
      this(jwtTokenProvider, userDetailsService, new ObjectMapper());
   }

   @Autowired
   public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService, ObjectMapper objectMapper) {
      this.jwtTokenProvider = jwtTokenProvider;
      this.userDetailsService = userDetailsService;
      this.objectMapper = objectMapper;
   }

   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      String authHeader = request.getHeader("Authorization");
      if (!StringUtils.isEmpty(authHeader) && StringUtils.startsWith(authHeader, "Bearer ")) {
         String jwt = authHeader.substring(7);

         try {
            String username = this.jwtTokenProvider.extractUsername(jwt);
            if (StringUtils.isNotEmpty(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
               UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
               if (this.jwtTokenProvider.validateToken(jwt, userDetails)) {
                  UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                  authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                  SecurityContextHolder.getContext().setAuthentication(authToken);
               }
            }
         } catch (ApiV1Exception exception) {
            if (exception.getCode() == ApiErrorCode.CATALOG_READ_UNAVAILABLE) {
               this.writeCatalogUnavailable(request, response, exception.getMessage());
               return;
            }

            log.warn("JWT authentication rejected with controlled code={}", exception.getCode());
         } catch (Exception exception) {
            log.warn("JWT authentication rejected type={}", exception.getClass().getSimpleName());
         }

         filterChain.doFilter(request, response);
      } else {
         filterChain.doFilter(request, response);
      }
   }

   private void writeCatalogUnavailable(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
      String correlationId = UUID.randomUUID().toString();
      ApiV1ErrorResponse body = new ApiV1ErrorResponse(
         Instant.now(),
         HttpStatus.SERVICE_UNAVAILABLE.value(),
         ApiErrorCode.CATALOG_READ_UNAVAILABLE.name(),
         message,
         request.getRequestURI(),
         correlationId,
         Map.of(),
         List.of()
      );
      response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      response.setContentType("application/json;charset=UTF-8");
      response.setHeader("X-Correlation-ID", correlationId);
      this.objectMapper.writeValue(response.getOutputStream(), body);
   }
}

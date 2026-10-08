package com.auralink.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("catalog-postgres-cutover-readonly")
@Order(Integer.MIN_VALUE)
public final class CutoverReadOnlyFilter extends OncePerRequestFilter {
   private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      if (SAFE_METHODS.contains(request.getMethod())) {
         filterChain.doFilter(request, response);
      } else {
         response.setStatus(503);
         response.setCharacterEncoding(StandardCharsets.UTF_8.name());
         response.setContentType("application/json");
         response.getWriter().write("{\"code\":\"CUTOVER_READ_ONLY\",\"message\":\"切换验收期间暂不接受写入。\"}");
      }
   }
}

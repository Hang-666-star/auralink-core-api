package com.auralink.config;

import com.auralink.config.properties.CorsProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
   private final CorsProperties corsProperties;

   @Bean
   public CorsConfigurationSource corsConfigurationSource() {
      this.validateCredentialPolicy(this.corsProperties.getAllowedOrigins(), this.corsProperties.isAllowCredentials());
      CorsConfiguration configuration = new CorsConfiguration();
      configuration.setAllowedOriginPatterns(List.copyOf(this.corsProperties.getAllowedOrigins()));
      configuration.setAllowedMethods(List.copyOf(this.corsProperties.getAllowedMethods()));
      configuration.setAllowedHeaders(List.copyOf(this.corsProperties.getAllowedHeaders()));
      configuration.setExposedHeaders(List.copyOf(this.corsProperties.getExposedHeaders()));
      configuration.setAllowCredentials(this.corsProperties.isAllowCredentials());
      configuration.setMaxAge(this.corsProperties.getMaxAgeSeconds());
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", configuration);
      return source;
   }

   private void validateCredentialPolicy(List<String> allowedOrigins, boolean allowCredentials) {
      if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
         if (allowCredentials && allowedOrigins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("Credentialed CORS must not use the wildcard origin");
         }
      } else {
         throw new IllegalStateException("At least one CORS origin must be configured");
      }
   }

   public CorsConfig(final CorsProperties corsProperties) {
      this.corsProperties = corsProperties;
   }
}

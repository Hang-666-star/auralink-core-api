package com.auralink.config;

import com.auralink.config.properties.GuideProperties;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GuideProperties.class)
public class GuideClientConfiguration {
   @Bean
   @Qualifier("guideRestClient")
   public RestClient guideRestClient(Builder sharedBuilder, GuideProperties properties) {
      this.validateInternalEndpoint(properties);
      int connectTimeoutMs = this.toMilliseconds(properties.getConnectTimeout(), "Guide connect timeout");
      int providerReadTimeoutMs = this.toMilliseconds(properties.getReadTimeout(), "Guide provider read timeout");
      int providerTotalTimeoutMs = this.toMilliseconds(properties.getTotalTimeout(), "Guide provider total timeout");
      int retryBackoffMs = this.toMilliseconds(properties.getRetryBackoff(), "Guide retry backoff");
      int internalReadTimeoutMs = this.toMilliseconds(properties.getInternalReadTimeout(), "Guide internal read timeout");
      long requiredBudgetMs = (long)providerTotalTimeoutMs + providerReadTimeoutMs + connectTimeoutMs;
      if (internalReadTimeoutMs <= requiredBudgetMs) {
         throw new IllegalArgumentException("Guide internal read timeout must exceed the bounded provider deadline budget");
      }

      SimpleClientHttpRequestFactory requestFactory = this.guideRequestFactory(connectTimeoutMs, internalReadTimeoutMs);
      return sharedBuilder.clone().requestFactory(requestFactory).build();
   }

   SimpleClientHttpRequestFactory guideRequestFactory(int connectTimeoutMs, int internalReadTimeoutMs) {
      SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setProxy(Proxy.NO_PROXY);
      requestFactory.setConnectTimeout(connectTimeoutMs);
      requestFactory.setReadTimeout(internalReadTimeoutMs);
      return requestFactory;
   }

   private void validateInternalEndpoint(GuideProperties properties) {
      try {
         URI endpoint = new URI(properties.getServiceUrl());
         String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
         String endpointHost = this.canonicalLoopback(endpoint.getHost());
         String bindHost = this.canonicalLoopback(properties.getServiceHost());
         int endpointPort = endpoint.getPort() < 0 ? 80 : endpoint.getPort();
         String path = endpoint.getPath();
         if (!"http".equals(scheme)
            || endpoint.getUserInfo() != null
            || endpoint.getQuery() != null
            || endpoint.getFragment() != null
            || path != null && !path.isBlank() && !"/".equals(path)
            || !endpointHost.equals(bindHost)
            || endpointPort != properties.getServicePort()) {
            throw new IllegalArgumentException("Guide service URL must match the configured loopback bind address");
         }
      } catch (URISyntaxException | NullPointerException exception) {
         throw new IllegalArgumentException("Guide service URL is invalid", exception);
      }
   }

   private String canonicalLoopback(String host) {
      if (host == null) {
         throw new IllegalArgumentException("Guide service host is invalid");
      }

      String normalized = host.strip().toLowerCase(Locale.ROOT);
      if (normalized.startsWith("[") && normalized.endsWith("]")) {
         normalized = normalized.substring(1, normalized.length() - 1);
      }

      if ("127.0.0.1".equals(normalized)) {
         return "127.0.0.1";
      } else if ("::1".equals(normalized)) {
         return "::1";
      } else {
         throw new IllegalArgumentException("Guide service host must be a loopback literal");
      }
   }

   private int toMilliseconds(Duration duration, String field) {
      if (duration != null && !duration.isZero() && !duration.isNegative()) {
         long milliseconds = duration.toMillis();
         if (milliseconds >= 1L && milliseconds <= 2147483647L) {
            return Math.toIntExact(milliseconds);
         } else {
            throw new IllegalArgumentException(field + " is outside the supported range");
         }
      } else {
         throw new IllegalArgumentException(field + " must be positive");
      }
   }
}

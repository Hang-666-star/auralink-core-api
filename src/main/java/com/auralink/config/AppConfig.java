package com.auralink.config;

import com.auralink.config.properties.HttpClientProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClient.Builder;

@Configuration
public class AppConfig {
   private final HttpClientProperties httpClientProperties;
   private final RestClientBuilderConfigurer restClientBuilderConfigurer;

   @Bean
   public ClientHttpRequestFactory clientHttpRequestFactory() {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(this.toIntMilliseconds(this.httpClientProperties.getConnectTimeout(), "connect timeout"));
      factory.setReadTimeout(this.toIntMilliseconds(this.httpClientProperties.getReadTimeout(), "read timeout"));
      return factory;
   }

   @Bean
   public RestTemplate restTemplate(ClientHttpRequestFactory requestFactory) {
      return new RestTemplate(requestFactory);
   }

   @Bean
   @Scope("prototype")
   public Builder restClientBuilder(ClientHttpRequestFactory requestFactory) {
      return this.restClientBuilderConfigurer.configure(RestClient.builder()).requestFactory(requestFactory);
   }

   private int toIntMilliseconds(Duration duration, String propertyName) {
      if (duration != null && !duration.isNegative() && !duration.isZero()) {
         long milliseconds = duration.toMillis();
         if (milliseconds >= 1L && milliseconds <= 2147483647L) {
            return (int)milliseconds;
         } else {
            throw new IllegalArgumentException(propertyName + " must be between 1ms and 2147483647ms");
         }
      } else {
         throw new IllegalArgumentException(propertyName + " must be positive");
      }
   }

   public AppConfig(final HttpClientProperties httpClientProperties, final RestClientBuilderConfigurer restClientBuilderConfigurer) {
      this.httpClientProperties = httpClientProperties;
      this.restClientBuilderConfigurer = restClientBuilderConfigurer;
   }
}

package com.auralink.config;

import com.auralink.config.properties.CriticProperties;
import java.net.Proxy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CriticProperties.class)
public class CriticClientConfiguration {
   @Bean(name = "criticRestClient")
   public RestClient criticRestClient(Builder builder, CriticProperties properties) {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setProxy(Proxy.NO_PROXY);
      factory.setConnectTimeout(properties.getConnectTimeout());
      factory.setReadTimeout(properties.getOverallDeadline());
      return builder.clone().requestFactory(factory).build();
   }
}

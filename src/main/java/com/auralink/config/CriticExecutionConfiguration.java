package com.auralink.config;

import com.auralink.config.properties.CriticProperties;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class CriticExecutionConfiguration {
   @Bean(name = "criticTaskExecutor")
   public ThreadPoolTaskExecutor criticTaskExecutor(CriticProperties properties) {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(properties.getMaxConcurrentEvaluations());
      executor.setMaxPoolSize(properties.getMaxConcurrentEvaluations());
      executor.setQueueCapacity(properties.getMaxConcurrentEvaluations());
      executor.setThreadNamePrefix("auralink-critic-");
      executor.setRejectedExecutionHandler(new AbortPolicy());
      executor.setWaitForTasksToCompleteOnShutdown(false);
      executor.setAcceptTasksAfterContextClose(false);
      return executor;
   }
}

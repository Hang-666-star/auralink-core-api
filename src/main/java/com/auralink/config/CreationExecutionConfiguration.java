package com.auralink.config;

import com.auralink.config.properties.CreationExecutionProperties;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(prefix = "auralink.creations", name = "enabled", havingValue = "true")
public class CreationExecutionConfiguration {
   @Bean(name = "creationWorkerExecutor")
   public ThreadPoolTaskExecutor creationWorkerExecutor(CreationExecutionProperties properties) {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(1);
      executor.setMaxPoolSize(1);
      executor.setQueueCapacity(properties.getExecutorQueueCapacity());
      executor.setThreadNamePrefix("auralink-creation-");
      executor.setRejectedExecutionHandler(new AbortPolicy());
      executor.setWaitForTasksToCompleteOnShutdown(true);
      executor.setAwaitTerminationSeconds(this.toBoundedSeconds(properties.getShutdownAwait()));
      executor.setAcceptTasksAfterContextClose(false);
      executor.setPhase(2147483547);
      return executor;
   }

   @Bean(name = "creationHeartbeatScheduler")
   public ThreadPoolTaskScheduler creationHeartbeatScheduler(CreationExecutionProperties properties) {
      ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
      scheduler.setPoolSize(1);
      scheduler.setThreadNamePrefix("auralink-creation-heartbeat-");
      scheduler.setRemoveOnCancelPolicy(true);
      scheduler.setWaitForTasksToCompleteOnShutdown(false);
      scheduler.setAwaitTerminationSeconds(this.toBoundedSeconds(properties.getShutdownAwait()));
      scheduler.setAcceptTasksAfterContextClose(false);
      scheduler.setPhase(2147483547);
      return scheduler;
   }

   @Bean(name = "creationRecoveryScheduler")
   public ThreadPoolTaskScheduler creationRecoveryScheduler(CreationExecutionProperties properties) {
      ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
      scheduler.setPoolSize(1);
      scheduler.setThreadNamePrefix("auralink-creation-recovery-");
      scheduler.setRemoveOnCancelPolicy(true);
      scheduler.setWaitForTasksToCompleteOnShutdown(false);
      scheduler.setAwaitTerminationSeconds(this.toBoundedSeconds(properties.getShutdownAwait()));
      scheduler.setAcceptTasksAfterContextClose(false);
      scheduler.setPhase(2147483547);
      return scheduler;
   }

   private int toBoundedSeconds(Duration duration) {
      if (duration != null && !duration.isNegative() && !duration.isZero()) {
         long seconds = Math.max(1L, duration.toSeconds());
         return (int)Math.min(seconds, 300L);
      } else {
         throw new IllegalArgumentException("Creation shutdown await duration must be positive");
      }
   }
}

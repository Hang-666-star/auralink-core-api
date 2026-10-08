package com.auralink.config.properties;

import com.auralink.workflow.WorkflowOperation;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.creations")
public class CreationExecutionProperties {
   private boolean enabled = false;
   @NotNull
   private Set<WorkflowOperation> enabledOperations = Set.of();
   @Min(1L)
   @Max(2L)
   private int maxExecutionTransformSteps = 2;
   @Min(1L)
   @Max(1L)
   private int workerCount = 1;
   @Min(1L)
   @Max(1000L)
   private int queueCapacity = 16;
   @Min(1L)
   @Max(1000L)
   private int executorQueueCapacity = 1;
   @NotNull
   private Duration leaseDuration = Duration.ofMinutes(15L);
   @NotNull
   private Duration heartbeatInterval = Duration.ofSeconds(30L);
   @NotNull
   private Duration recoveryGrace = Duration.ofSeconds(90L);
   @NotNull
   private Duration recoveryInterval = Duration.ofSeconds(60L);
   @Min(1L)
   @Max(100L)
   private int recoveryBatchSize = 50;
   @Min(1L)
   @Max(100L)
   private int startupMaxBatches = 20;
   @NotNull
   private Duration recoveryFenceLease = Duration.ofMinutes(5L);
   @NotNull
   private Duration dispatchDelay = Duration.ofSeconds(1L);
   @NotNull
   private Duration shutdownAwait = Duration.ofSeconds(30L);
   @Min(1L)
   @Max(100L)
   private int defaultPageSize = 20;
   @Min(1L)
   @Max(100L)
   private int maxPageSize = 100;

   @AssertTrue(message = "Creation heartbeat interval must be positive and shorter than the lease duration")
   public boolean isHeartbeatTimingSafe() {
      return this.positive(this.leaseDuration) && this.positive(this.heartbeatInterval) && this.heartbeatInterval.compareTo(this.leaseDuration) < 0;
   }

   @AssertTrue(message = "Creation recovery durations must be positive and the fence lease must be at least five minutes")
   public boolean isRecoveryTimingSafe() {
      return this.positive(this.recoveryGrace)
         && this.positive(this.recoveryInterval)
         && this.recoveryFenceLease != null
         && this.recoveryFenceLease.compareTo(Duration.ofMinutes(5L)) >= 0;
   }

   @AssertTrue(message = "Creation shutdown wait must be positive and bounded")
   public boolean isShutdownAwaitSafe() {
      return this.shutdownAwait != null
         && !this.shutdownAwait.isNegative()
         && !this.shutdownAwait.isZero()
         && this.shutdownAwait.compareTo(Duration.ofMinutes(5L)) <= 0;
   }

   private boolean positive(Duration value) {
      return value != null && !value.isNegative() && !value.isZero();
   }

   public boolean isOperationEnabled(WorkflowOperation operation) {
      return operation != null && this.enabledOperations != null && this.enabledOperations.contains(operation);
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public Set<WorkflowOperation> getEnabledOperations() {
      return this.enabledOperations;
   }

   public int getMaxExecutionTransformSteps() {
      return this.maxExecutionTransformSteps;
   }

   public int getWorkerCount() {
      return this.workerCount;
   }

   public int getQueueCapacity() {
      return this.queueCapacity;
   }

   public int getExecutorQueueCapacity() {
      return this.executorQueueCapacity;
   }

   public Duration getLeaseDuration() {
      return this.leaseDuration;
   }

   public Duration getHeartbeatInterval() {
      return this.heartbeatInterval;
   }

   public Duration getRecoveryGrace() {
      return this.recoveryGrace;
   }

   public Duration getRecoveryInterval() {
      return this.recoveryInterval;
   }

   public int getRecoveryBatchSize() {
      return this.recoveryBatchSize;
   }

   public int getStartupMaxBatches() {
      return this.startupMaxBatches;
   }

   public Duration getRecoveryFenceLease() {
      return this.recoveryFenceLease;
   }

   public Duration getDispatchDelay() {
      return this.dispatchDelay;
   }

   public Duration getShutdownAwait() {
      return this.shutdownAwait;
   }

   public int getDefaultPageSize() {
      return this.defaultPageSize;
   }

   public int getMaxPageSize() {
      return this.maxPageSize;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setEnabledOperations(final Set<WorkflowOperation> enabledOperations) {
      this.enabledOperations = enabledOperations;
   }

   public void setMaxExecutionTransformSteps(final int maxExecutionTransformSteps) {
      this.maxExecutionTransformSteps = maxExecutionTransformSteps;
   }

   public void setWorkerCount(final int workerCount) {
      this.workerCount = workerCount;
   }

   public void setQueueCapacity(final int queueCapacity) {
      this.queueCapacity = queueCapacity;
   }

   public void setExecutorQueueCapacity(final int executorQueueCapacity) {
      this.executorQueueCapacity = executorQueueCapacity;
   }

   public void setLeaseDuration(final Duration leaseDuration) {
      this.leaseDuration = leaseDuration;
   }

   public void setHeartbeatInterval(final Duration heartbeatInterval) {
      this.heartbeatInterval = heartbeatInterval;
   }

   public void setRecoveryGrace(final Duration recoveryGrace) {
      this.recoveryGrace = recoveryGrace;
   }

   public void setRecoveryInterval(final Duration recoveryInterval) {
      this.recoveryInterval = recoveryInterval;
   }

   public void setRecoveryBatchSize(final int recoveryBatchSize) {
      this.recoveryBatchSize = recoveryBatchSize;
   }

   public void setStartupMaxBatches(final int startupMaxBatches) {
      this.startupMaxBatches = startupMaxBatches;
   }

   public void setRecoveryFenceLease(final Duration recoveryFenceLease) {
      this.recoveryFenceLease = recoveryFenceLease;
   }

   public void setDispatchDelay(final Duration dispatchDelay) {
      this.dispatchDelay = dispatchDelay;
   }

   public void setShutdownAwait(final Duration shutdownAwait) {
      this.shutdownAwait = shutdownAwait;
   }

   public void setDefaultPageSize(final int defaultPageSize) {
      this.defaultPageSize = defaultPageSize;
   }

   public void setMaxPageSize(final int maxPageSize) {
      this.maxPageSize = maxPageSize;
   }
}

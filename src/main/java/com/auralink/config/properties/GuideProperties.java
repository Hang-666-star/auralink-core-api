package com.auralink.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.guide")
public class GuideProperties {
   private boolean enabled = false;
   private String serviceUrl = "";
   @NotNull
   private Duration connectTimeout = Duration.ofSeconds(5L);
   @NotNull
   private Duration readTimeout = Duration.ofSeconds(120L);
   @NotNull
   private Duration totalTimeout = Duration.ofSeconds(250L);
   @NotNull
   private Duration internalReadTimeout = Duration.ofSeconds(380L);
   @NotNull
   private Duration retryBackoff = Duration.ofMillis(200L);
   @Min(1L)
   @Max(8L)
   private int maxConcurrentGenerations = 2;
   @Min(1L)
   @Max(100L)
   private int userGenerationLimit = 3;
   @NotNull
   private Duration userGenerationWindow = Duration.ofMinutes(10L);
   @Min(1L)
   @Max(10000L)
   private int globalGenerationLimit = 30;
   @NotNull
   private Duration globalGenerationWindow = Duration.ofHours(1L);
   @NotBlank
   @Pattern(regexp = "1")
   private String schemaVersion = "1";
   @Min(0L)
   @Max(5L)
   private int maxKnowledgeItems = 5;
   @Min(0L)
   @Max(8000L)
   private int maxKnowledgeChars = 8000;
   @NotBlank
   private String poetryGraphPath = "../frontend/public/data/poetry-graph.json";
   @NotBlank
   private String poetryStatsPath = "../frontend/public/data/poetry-stats.json";
   private String knowledgeRoot = "";
   private String internalToken = "";
   @NotBlank
   private String serviceHost = "127.0.0.1";
   @Min(1L)
   @Max(65535L)
   private int servicePort = 5003;

   public void setServiceUrl(String serviceUrl) {
      this.serviceUrl = this.normalizeSimpleQuotedValue(serviceUrl);
   }

   public String getServiceUrl() {
      if (this.serviceUrl != null && !this.serviceUrl.isBlank()) {
         return this.serviceUrl;
      }

      String host = this.serviceHost == null ? "" : this.serviceHost.strip();
      String authority = "::1".equals(host) ? "[::1]" : host;
      return "http://" + authority + ":" + this.servicePort;
   }

   public void setSchemaVersion(String schemaVersion) {
      this.schemaVersion = this.normalizeSimpleQuotedValue(schemaVersion);
   }

   public void setPoetryGraphPath(String poetryGraphPath) {
      this.poetryGraphPath = this.normalizeSimpleQuotedValue(poetryGraphPath);
   }

   public void setPoetryStatsPath(String poetryStatsPath) {
      this.poetryStatsPath = this.normalizeSimpleQuotedValue(poetryStatsPath);
   }

   public void setKnowledgeRoot(String knowledgeRoot) {
      this.knowledgeRoot = this.normalizeSimpleQuotedValue(knowledgeRoot);
   }

   public void setInternalToken(String internalToken) {
      this.internalToken = this.normalizeSimpleQuotedValue(internalToken);
   }

   public void setServiceHost(String serviceHost) {
      this.serviceHost = this.normalizeSimpleQuotedValue(serviceHost);
   }

   private String normalizeSimpleQuotedValue(String value) {
      if (value != null && value.length() >= 2) {
         char first = value.charAt(0);
         char last = value.charAt(value.length() - 1);
         return first != last || first != '\'' && first != '"' ? value : value.substring(1, value.length() - 1);
      } else {
         return value;
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public Duration getConnectTimeout() {
      return this.connectTimeout;
   }

   public Duration getReadTimeout() {
      return this.readTimeout;
   }

   public Duration getTotalTimeout() {
      return this.totalTimeout;
   }

   public Duration getInternalReadTimeout() {
      return this.internalReadTimeout;
   }

   public Duration getRetryBackoff() {
      return this.retryBackoff;
   }

   public int getMaxConcurrentGenerations() {
      return this.maxConcurrentGenerations;
   }

   public int getUserGenerationLimit() {
      return this.userGenerationLimit;
   }

   public Duration getUserGenerationWindow() {
      return this.userGenerationWindow;
   }

   public int getGlobalGenerationLimit() {
      return this.globalGenerationLimit;
   }

   public Duration getGlobalGenerationWindow() {
      return this.globalGenerationWindow;
   }

   public String getSchemaVersion() {
      return this.schemaVersion;
   }

   public int getMaxKnowledgeItems() {
      return this.maxKnowledgeItems;
   }

   public int getMaxKnowledgeChars() {
      return this.maxKnowledgeChars;
   }

   public String getPoetryGraphPath() {
      return this.poetryGraphPath;
   }

   public String getPoetryStatsPath() {
      return this.poetryStatsPath;
   }

   public String getKnowledgeRoot() {
      return this.knowledgeRoot;
   }

   public String getInternalToken() {
      return this.internalToken;
   }

   public String getServiceHost() {
      return this.serviceHost;
   }

   public int getServicePort() {
      return this.servicePort;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setConnectTimeout(final Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
   }

   public void setReadTimeout(final Duration readTimeout) {
      this.readTimeout = readTimeout;
   }

   public void setTotalTimeout(final Duration totalTimeout) {
      this.totalTimeout = totalTimeout;
   }

   public void setInternalReadTimeout(final Duration internalReadTimeout) {
      this.internalReadTimeout = internalReadTimeout;
   }

   public void setRetryBackoff(final Duration retryBackoff) {
      this.retryBackoff = retryBackoff;
   }

   public void setMaxConcurrentGenerations(final int maxConcurrentGenerations) {
      this.maxConcurrentGenerations = maxConcurrentGenerations;
   }

   public void setUserGenerationLimit(final int userGenerationLimit) {
      this.userGenerationLimit = userGenerationLimit;
   }

   public void setUserGenerationWindow(final Duration userGenerationWindow) {
      this.userGenerationWindow = userGenerationWindow;
   }

   public void setGlobalGenerationLimit(final int globalGenerationLimit) {
      this.globalGenerationLimit = globalGenerationLimit;
   }

   public void setGlobalGenerationWindow(final Duration globalGenerationWindow) {
      this.globalGenerationWindow = globalGenerationWindow;
   }

   public void setMaxKnowledgeItems(final int maxKnowledgeItems) {
      this.maxKnowledgeItems = maxKnowledgeItems;
   }

   public void setMaxKnowledgeChars(final int maxKnowledgeChars) {
      this.maxKnowledgeChars = maxKnowledgeChars;
   }

   public void setServicePort(final int servicePort) {
      this.servicePort = servicePort;
   }
}

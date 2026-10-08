package com.auralink.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auralink.critic")
public class CriticProperties {
   private boolean enabled = false;
   private String serviceUrl = "";
   private String internalToken = "";
   @NotNull
   private Duration connectTimeout = Duration.ofSeconds(5L);
   @NotNull
   private Duration overallDeadline = Duration.ofSeconds(380L);
   @Min(1L)
   @Max(2L)
   private int maxConcurrentEvaluations = 1;
   private String evaluatorVersion = "1.0.0";
   private String modelIdentity = "";
   private String promptSchemaVersion = "1";

   public boolean isConfigured() {
      return this.enabled
         && this.nonBlank(this.serviceUrl)
         && this.nonBlank(this.internalToken)
         && this.nonBlank(this.evaluatorVersion)
         && this.nonBlank(this.modelIdentity)
         && this.nonBlank(this.promptSchemaVersion)
         && this.isLoopbackHttpUrl(this.serviceUrl)
         && this.positive(this.connectTimeout)
         && this.positive(this.overallDeadline);
   }

   private boolean isLoopbackHttpUrl(String value) {
      try {
         URI uri = new URI(value);
         String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
         return "http".equalsIgnoreCase(uri.getScheme())
            && uri.getUserInfo() == null
            && uri.getQuery() == null
            && uri.getFragment() == null
            && ("127.0.0.1".equals(host) || "::1".equals(host));
      } catch (URISyntaxException exception) {
         return false;
      }
   }

   private boolean nonBlank(String value) {
      return value != null && !value.isBlank();
   }

   private boolean positive(Duration value) {
      return value != null && !value.isNegative() && !value.isZero();
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public String getServiceUrl() {
      return this.serviceUrl;
   }

   public String getInternalToken() {
      return this.internalToken;
   }

   public Duration getConnectTimeout() {
      return this.connectTimeout;
   }

   public Duration getOverallDeadline() {
      return this.overallDeadline;
   }

   public int getMaxConcurrentEvaluations() {
      return this.maxConcurrentEvaluations;
   }

   public String getEvaluatorVersion() {
      return this.evaluatorVersion;
   }

   public String getModelIdentity() {
      return this.modelIdentity;
   }

   public String getPromptSchemaVersion() {
      return this.promptSchemaVersion;
   }

   public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
   }

   public void setServiceUrl(final String serviceUrl) {
      this.serviceUrl = serviceUrl;
   }

   public void setInternalToken(final String internalToken) {
      this.internalToken = internalToken;
   }

   public void setConnectTimeout(final Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
   }

   public void setOverallDeadline(final Duration overallDeadline) {
      this.overallDeadline = overallDeadline;
   }

   public void setMaxConcurrentEvaluations(final int maxConcurrentEvaluations) {
      this.maxConcurrentEvaluations = maxConcurrentEvaluations;
   }

   public void setEvaluatorVersion(final String evaluatorVersion) {
      this.evaluatorVersion = evaluatorVersion;
   }

   public void setModelIdentity(final String modelIdentity) {
      this.modelIdentity = modelIdentity;
   }

   public void setPromptSchemaVersion(final String promptSchemaVersion) {
      this.promptSchemaVersion = promptSchemaVersion;
   }
}

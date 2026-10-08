package com.auralink.provider.seedream;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.config.properties.ProviderProperties;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.creation.provider.ProviderReadiness;
import com.auralink.creation.provider.ProviderReadinessState;
import com.auralink.provider.validation.CreationProviderConfigurationChecks;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SeedreamEndpointPolicy implements SeedreamEndpointResolver {
   private static final String APPROVED_HOST = "ark.cn-beijing.volces.com";
   private static final String APPROVED_ROOT_PATH = "/api/v3";
   private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}");
   private final CreationProviderProperties creationProperties;
   private final ProviderProperties.Provider provider;

   public SeedreamEndpointPolicy(CreationProviderProperties creationProperties, ProviderProperties providerProperties) {
      this.creationProperties = creationProperties;
      this.provider = providerProperties.getSeedream();
   }

   public ProviderReadiness readiness() {
      if (!this.creationProperties.isEnabled()) {
         return this.readiness(ProviderReadinessState.FEATURE_DISABLED, "CREATION_PROVIDERS_DISABLED");
      }

      if (!this.isBlank(this.provider.getApiKey()) && !this.isBlank(this.provider.getBaseUrl()) && !this.isBlank(this.provider.getModel())) {
         try {
            this.requireValidModel(this.provider.getModel());
            this.validateBase(this.provider.getBaseUrl());
            CreationProviderConfigurationChecks.requireSeedream(this.creationProperties);
            this.requireSafeGenerationSettings();
            return this.readiness(ProviderReadinessState.READY_FOR_CONTROLLED_EXECUTION, "CONFIGURATION_VALIDATED_WITHOUT_PROVIDER_CALL");
         } catch (ProviderExecutionException exception) {
            return this.readiness(ProviderReadinessState.CONFIGURATION_INVALID, "PROVIDER_CONFIGURATION_INVALID");
         }
      } else {
         return this.readiness(ProviderReadinessState.CONFIGURATION_MISSING, "REQUIRED_CONFIGURATION_MISSING");
      }
   }

   @Override
   public URI resolveGenerationEndpoint() {
      this.requireFeatureEnabled();
      this.requirePresentConfiguration();
      this.requireValidModel(this.provider.getModel());
      URI base = this.validateBase(this.provider.getBaseUrl());
      CreationProviderConfigurationChecks.requireSeedream(this.creationProperties);
      this.requireSafeGenerationSettings();
      return URI.create(base.toString() + "/images/generations");
   }

   private URI validateBase(String rawBase) {
      URI uri;
      try {
         uri = new URI(rawBase.trim());
      } catch (URISyntaxException | NullPointerException exception) {
         throw this.invalid("Seedream base URL is invalid", exception);
      }

      String path = uri.getPath();

      while (path != null && path.length() > 1 && path.endsWith("/")) {
         path = path.substring(0, path.length() - 1);
      }

      if ("https".equalsIgnoreCase(uri.getScheme())
         && uri.getHost() != null
         && "ark.cn-beijing.volces.com".equals(uri.getHost().toLowerCase(Locale.ROOT))
         && uri.getPort() == -1
         && uri.getUserInfo() == null
         && uri.getQuery() == null
         && uri.getFragment() == null
         && "/api/v3".equals(path)) {
         return URI.create("https://ark.cn-beijing.volces.com/api/v3");
      } else {
         throw this.invalid("Seedream base URL is not an approved Ark root", null);
      }
   }

   private void requireFeatureEnabled() {
      if (!this.creationProperties.isEnabled()) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_FEATURE_DISABLED, "Creation providers are disabled");
      }
   }

   private void requirePresentConfiguration() {
      if (this.isBlank(this.provider.getApiKey()) || this.isBlank(this.provider.getBaseUrl()) || this.isBlank(this.provider.getModel())) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_CONFIGURATION_MISSING, "Seedream configuration is incomplete");
      }
   }

   private void requireValidModel(String model) {
      if (model == null || !MODEL.matcher(model.trim()).matches()) {
         throw this.invalid("Seedream model configuration is invalid", null);
      }
   }

   private void requireSafeGenerationSettings() {
      String size = this.creationProperties.getSeedreamDefaultSize();
      String format = this.creationProperties.getSeedreamOutputFormat();
      if (size == null || !size.matches("(?:1K|2K|4K)") || format == null || !format.equals("png") && !format.equals("jpeg")) {
         throw this.invalid("Seedream generation settings are invalid", null);
      }
   }

   private boolean isBlank(String value) {
      return value == null || value.isBlank();
   }

   private ProviderReadiness readiness(ProviderReadinessState state, String reason) {
      return new ProviderReadiness(state, reason);
   }

   private ProviderExecutionException invalid(String message, Throwable cause) {
      return cause == null
         ? new ProviderExecutionException(ProviderErrorCategory.PROVIDER_CONFIGURATION_INVALID, message)
         : new ProviderExecutionException(ProviderErrorCategory.PROVIDER_CONFIGURATION_INVALID, message, cause);
   }
}

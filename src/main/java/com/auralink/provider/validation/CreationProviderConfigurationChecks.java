package com.auralink.provider.validation;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import java.time.Duration;

public final class CreationProviderConfigurationChecks {
   private CreationProviderConfigurationChecks() {
   }

   public static void requireSeedream(CreationProviderProperties properties) {
      requireCommon(properties);
      requirePositive(properties.getSeedreamReadTimeout());
      if (properties.getMaxImageOutputBytes() < 1L || properties.getMaxConcurrentSeedream() < 1) {
         throw invalid();
      }
   }

   public static void requireQwen(CreationProviderProperties properties) {
      requireCommon(properties);
      requirePositive(properties.getQwenReadTimeout());
      if (properties.getMaxConcurrentQwen() < 1) {
         throw invalid();
      }
   }

   public static void requireVmm(CreationProviderProperties properties) {
      requireCommon(properties);
      requirePositive(properties.getVmmReadTimeout());
      if (properties.getMaxAudioOutputBytes() < 1L || properties.getMaxConcurrentVmm() < 1) {
         throw invalid();
      }
   }

   private static void requireCommon(CreationProviderProperties properties) {
      if (properties != null
         && properties.getStagingDir() != null
         && properties.getStagingDir().isAbsolute()
         && properties.getMaxImageInputBytes() >= 1L
         && properties.getMaxTextChars() >= 1) {
         requirePositive(properties.getConnectTimeout());
      } else {
         throw invalid();
      }
   }

   private static void requirePositive(Duration duration) {
      if (duration == null || duration.isZero() || duration.isNegative()) {
         throw invalid();
      }
   }

   private static ProviderExecutionException invalid() {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_CONFIGURATION_INVALID, "Creation provider bounds are invalid");
   }
}

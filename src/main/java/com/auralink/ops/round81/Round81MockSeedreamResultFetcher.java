package com.auralink.ops.round81;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.provider.artifact.ProviderArtifact;
import com.auralink.provider.artifact.ProviderArtifactStagingService;
import com.auralink.provider.seedream.SeedreamResultFetcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

final class Round81MockSeedreamResultFetcher implements SeedreamResultFetcher {
   private static final int BUFFER_SIZE = 16384;
   private final ProviderArtifactStagingService stagingService;
   private final CreationProviderProperties properties;
   private final Round81MockSupport mockSupport;

   Round81MockSeedreamResultFetcher(ProviderArtifactStagingService stagingService, CreationProviderProperties properties, Round81MockSupport mockSupport) {
      this.stagingService = stagingService;
      this.properties = properties;
      this.mockSupport = mockSupport;
   }

   @Override
   public void prepare() {
      this.mockSupport.requireEnabled();
      this.stagingService.prepare();
   }

   @Override
   public ProviderArtifact fetch(String resultUrl) {
      URI expected = this.mockSupport.generatedImage();

      URI actual;
      try {
         actual = URI.create(resultUrl);
      } catch (IllegalArgumentException exception) {
         throw this.invalid("Mock Seedream result URL is invalid", exception);
      }

      if (!expected.equals(actual)) {
         throw this.invalid("Mock Seedream result URL is outside the fixed loopback fixture", null);
      } else {
         return this.stagingService.stageOutputImage(target -> this.download(expected, target));
      }
   }

   private void download(URI source, Path target) throws IOException {
      HttpURLConnection connection = (HttpURLConnection)source.toURL().openConnection(Proxy.NO_PROXY);
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(this.toMillis(this.properties.getConnectTimeout()));
      connection.setReadTimeout(this.toMillis(this.properties.getSeedreamReadTimeout()));
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "image/png,image/jpeg");

      try {
         if (connection.getResponseCode() != 200) {
            throw new IOException("Mock image fixture returned a non-success status");
         }

         long declared = connection.getContentLengthLong();
         if (declared > this.properties.getMaxImageOutputBytes()) {
            throw new IOException("Mock image fixture exceeds the byte limit");
         }

         try (
            InputStream input = connection.getInputStream();
            OutputStream output = Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
         ) {
            this.copyBounded(input, output, this.properties.getMaxImageOutputBytes());
         }
      } finally {
         connection.disconnect();
      }
   }

   private void copyBounded(InputStream input, OutputStream output, long maximum) throws IOException {
      byte[] buffer = new byte[16384];
      long total = 0L;

      int read;
      while ((read = input.read(buffer)) >= 0) {
         if (read != 0) {
            if (total > maximum - read) {
               throw new IOException("Mock image fixture exceeds the byte limit");
            }

            output.write(buffer, 0, read);
            total += read;
         }
      }
   }

   private int toMillis(Duration duration) {
      long millis = duration.toMillis();
      if (millis >= 1L && millis <= 2147483647L) {
         return (int)millis;
      } else {
         throw this.invalid("Mock transport timeout configuration is invalid", null);
      }
   }

   private ProviderExecutionException invalid(String message, Throwable cause) {
      return cause == null
         ? new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, message)
         : new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, message, cause);
   }
}

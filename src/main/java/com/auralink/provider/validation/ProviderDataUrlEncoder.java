package com.auralink.provider.validation;

import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.provider.artifact.ProviderArtifact;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ProviderDataUrlEncoder {
   private static final int BUFFER_SIZE = 65536;

   public String encodeImage(ProviderArtifact artifact, long maxBytes) {
      if (artifact != null
         && artifact.isAvailable()
         && artifact.byteLength() <= maxBytes
         && ("image/jpeg".equals(artifact.mimeType()) || "image/png".equals(artifact.mimeType()))) {
         long encodedLength = (artifact.byteLength() + 2L) / 3L * 4L;
         if (encodedLength > 2147483519L) {
            throw this.invalid("Provider image encoded payload is too large");
         }

         ByteArrayOutputStream encoded = new ByteArrayOutputStream((int)encodedLength);

         try (
            InputStream input = artifact.openStream();
            OutputStream base64 = Base64.getEncoder().wrap(encoded);
         ) {
            byte[] buffer = new byte[65536];
            long total = 0L;

            int read;
            while ((read = input.read(buffer)) >= 0) {
               if (read != 0) {
                  if (total > maxBytes - read) {
                     throw this.invalid("Provider image exceeds the configured byte limit");
                  }

                  base64.write(buffer, 0, read);
                  total += read;
               }
            }

            if (total != artifact.byteLength()) {
               throw this.invalid("Provider image changed during encoding");
            }
         } catch (ProviderExecutionException exception) {
            throw exception;
         } catch (IOException exception) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider image could not be encoded", exception);
         }

         return "data:" + artifact.mimeType() + ";base64," + encoded.toString(StandardCharsets.US_ASCII);
      } else {
         throw this.invalid("Provider image cannot be encoded");
      }
   }

   private ProviderExecutionException invalid(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, message);
   }
}

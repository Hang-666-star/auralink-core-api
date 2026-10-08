package com.auralink.provider.artifact;

import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProviderArtifact implements AutoCloseable {
   private final Path containedPath;
   private final Path stagingRoot;
   private final String mimeType;
   private final String fileExtension;
   private final long byteLength;
   private final String sha256;
   private final Integer width;
   private final Integer height;
   private final AtomicBoolean closed = new AtomicBoolean(false);

   ProviderArtifact(Path containedPath, Path stagingRoot, String mimeType, String fileExtension, long byteLength, String sha256, Integer width, Integer height) {
      this.containedPath = containedPath;
      this.stagingRoot = stagingRoot;
      this.mimeType = mimeType;
      this.fileExtension = fileExtension;
      this.byteLength = byteLength;
      this.sha256 = sha256;
      this.width = width;
      this.height = height;
   }

   public String mimeType() {
      return this.mimeType;
   }

   public String fileExtension() {
      return this.fileExtension;
   }

   public long byteLength() {
      return this.byteLength;
   }

   public String sha256() {
      return this.sha256;
   }

   public Integer width() {
      return this.width;
   }

   public Integer height() {
      return this.height;
   }

   public boolean isAvailable() {
      return !this.closed.get() && Files.isRegularFile(this.containedPath, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(this.containedPath);
   }

   public InputStream openStream() {
      this.requireAvailable();

      try {
         return Files.newInputStream(this.containedPath);
      } catch (IOException exception) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact is unavailable", exception);
      }
   }

   @Override
   public synchronized void close() {
      if (!this.closed.get()) {
         this.requireDirectContainedPath();

         try {
            Files.deleteIfExists(this.containedPath);
            this.closed.set(true);
         } catch (IOException exception) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact cleanup failed", exception);
         }
      }
   }

   private void requireAvailable() {
      this.requireDirectContainedPath();
      if (!this.isAvailable()) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact is unavailable");
      }

      try {
         if (Files.size(this.containedPath) != this.byteLength) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact changed after validation");
         }

         MessageDigest digest = MessageDigest.getInstance("SHA-256");

         try (InputStream input = Files.newInputStream(this.containedPath)) {
            byte[] buffer = new byte[65536];

            int read;
            while ((read = input.read(buffer)) >= 0) {
               if (read > 0) {
                  digest.update(buffer, 0, read);
               }
            }
         }

         if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(this.sha256))) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact changed after validation");
         }
      } catch (IOException exception) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact could not be verified", exception);
      } catch (NoSuchAlgorithmException | IllegalArgumentException exception) {
         throw new ProviderExecutionException(
            ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, "Provider artifact checksum could not be verified", exception
         );
      }
   }

   private void requireDirectContainedPath() {
      Path normalized = this.containedPath.toAbsolutePath().normalize();
      if (!normalized.startsWith(this.stagingRoot) || normalized.getParent() == null || !normalized.getParent().equals(this.stagingRoot)) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, "Provider artifact containment check failed");
      }
   }
}

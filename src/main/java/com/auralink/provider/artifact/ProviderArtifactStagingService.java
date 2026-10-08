package com.auralink.provider.artifact;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.service.media.ImageContentValidator;
import com.auralink.service.media.InvalidImageContentException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProviderArtifactStagingService {
   private static final int BUFFER_SIZE = 65536;
   private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
   );
   private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
   private final CreationProviderProperties properties;
   private final ImageContentValidator imageContentValidator;
   private final AudioOutputValidator audioOutputValidator;

   public ProviderArtifact stageInputImage(InputStream input, String declaredMimeType) {
      return this.stageImage(input, declaredMimeType, this.properties.getMaxImageInputBytes());
   }

   public ProviderArtifact stageOutputImage(InputStream input, String declaredMimeType) {
      return this.stageImage(input, declaredMimeType, this.properties.getMaxImageOutputBytes());
   }

   public ProviderArtifact stageOutputImage(ProviderArtifactWriter writer) {
      return this.stageWrittenImage(writer, null, this.properties.getMaxImageOutputBytes());
   }

   public ProviderArtifact stageOutputWave(InputStream input) {
      return this.stageWave(input, this.properties.getMaxAudioOutputBytes());
   }

   public ProviderArtifact stageOutputWave(ProviderArtifactWriter writer) {
      return this.stageWrittenWave(writer, this.properties.getMaxAudioOutputBytes());
   }

   public void prepare() {
      try {
         this.ensureStagingRoot();
      } catch (IOException exception) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_CONFIGURATION_INVALID, "Provider staging root is unavailable or unsafe", exception);
      }
   }

   private ProviderArtifact stageImage(InputStream input, String declaredMimeType, long maxBytes) {
      if (input == null) {
         throw this.invalidContract("Provider image stream is required");
      } else {
         return this.stageWrittenImage(target -> {
            try (
               InputStream source = input;
               OutputStream destination = Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            ) {
               copyWithLimit(source, destination, maxBytes);
            }
         }, declaredMimeType, maxBytes);
      }
   }

   private ProviderArtifact stageWave(InputStream input, long maxBytes) {
      if (input == null) {
         throw this.invalidContract("Provider audio stream is required");
      } else {
         return this.stageWrittenWave(target -> {
            try (
               InputStream source = input;
               OutputStream destination = Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            ) {
               copyWithLimit(source, destination, maxBytes);
            }
         }, maxBytes);
      }
   }

   private ProviderArtifact stageWrittenImage(ProviderArtifactWriter writer, String declaredMimeType, long maxBytes) {
      return this.stage(writer, maxBytes, partial -> {
         ImageContentValidator.ValidatedImage image;
         try {
            image = this.imageContentValidator.validateTrustedImage(partial);
         } catch (InvalidImageContentException exception) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider image failed content validation", exception);
         }

         if (declaredMimeType != null && !this.normalizeMime(declaredMimeType).equals(image.mimeType())) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider image MIME does not match its bytes");
         } else {
            return new ProviderArtifactStagingService.ValidatedBinary(image.mimeType(), image.fileExtension(), image.width(), image.height());
         }
      });
   }

   private ProviderArtifact stageWrittenWave(ProviderArtifactWriter writer, long maxBytes) {
      return this.stage(writer, maxBytes, partial -> {
         this.audioOutputValidator.validateWave(partial, "audio/wav", maxBytes);
         return new ProviderArtifactStagingService.ValidatedBinary("audio/wav", "wav", null, null);
      });
   }

   private ProviderArtifact stage(ProviderArtifactWriter writer, long maxBytes, ProviderArtifactStagingService.BinaryValidator validator) {
      if (writer != null && maxBytes >= 1L) {
         Path partial = null;
         Path completed = null;

         try {
            Path root = this.ensureStagingRoot();
            partial = Files.createTempFile(root, ".provider-incoming-", ".part");
            this.setFilePermissions(partial);
            writer.write(partial);
            this.requireContainedRegularFile(root, partial);
            ProviderArtifactStagingService.DigestMetadata digest = this.digestWithLimit(partial, maxBytes);
            if (digest.byteLength() < 1L) {
               throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact is empty");
            }

            ProviderArtifactStagingService.ValidatedBinary binary = validator.validate(partial);
            completed = root.resolve(UUID.randomUUID() + "." + binary.extension()).normalize();
            this.requireDirectChild(root, completed);
            Files.move(partial, completed, StandardCopyOption.ATOMIC_MOVE);
            partial = null;
            this.setFilePermissions(completed);
            this.requireContainedRegularFile(root, completed);
            return new ProviderArtifact(
               completed, root, binary.mimeType(), binary.extension(), digest.byteLength(), digest.sha256(), binary.width(), binary.height()
            );
         } catch (ProviderExecutionException exception) {
            this.deleteQuietly(partial);
            this.deleteQuietly(completed);
            throw exception;
         } catch (Exception exception) {
            this.deleteQuietly(partial);
            this.deleteQuietly(completed);
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact staging failed", exception);
         }
      } else {
         throw this.invalidContract("Provider staging writer and byte limit are required");
      }
   }

   private Path ensureStagingRoot() throws IOException {
      Path configured = this.properties.getStagingDir();
      if (configured == null) {
         throw new IOException("Provider staging root is unavailable");
      } else if (!configured.isAbsolute()) {
         throw new IOException("Provider staging root must be absolute");
      } else {
         Path normalized = configured.normalize();
         if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(normalized)) {
            throw new IOException("Provider staging root is unsafe");
         } else {
            Files.createDirectories(normalized);
            if (Files.isSymbolicLink(normalized)) {
               throw new IOException("Provider staging root is unsafe");
            } else {
               Path real = normalized.toRealPath();
               if (real.equals(normalized) && Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
                  this.setDirectoryPermissions(real);
                  return real;
               } else {
                  throw new IOException("Provider staging root is unsafe");
               }
            }
         }
      }
   }

   private void requireContainedRegularFile(Path root, Path candidate) throws IOException {
      this.requireDirectChild(root, candidate);
      if (!Files.isSymbolicLink(candidate) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
         Path real = candidate.toRealPath();
         if (!real.startsWith(root) || !real.getParent().equals(root)) {
            throw new IOException("Provider staging target escaped its root");
         }
      } else {
         throw new IOException("Provider staging target is unsafe");
      }
   }

   private void requireDirectChild(Path root, Path candidate) throws IOException {
      Path normalized = candidate.toAbsolutePath().normalize();
      if (!normalized.startsWith(root) || normalized.getParent() == null || !normalized.getParent().equals(root)) {
         throw new IOException("Provider staging target escaped its root");
      }
   }

   private ProviderArtifactStagingService.DigestMetadata digestWithLimit(Path path, long maxBytes) throws IOException, NoSuchAlgorithmException {
      long declaredSize = Files.size(path);
      if (declaredSize > maxBytes) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact exceeds the configured byte limit");
      }

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      InputStream input = new DigestInputStream(Files.newInputStream(path), digest);

      long total;
      try {
         total = copyWithLimit(input, OutputStream.nullOutputStream(), maxBytes);
      } catch (Throwable var13) {
         try {
            input.close();
         } catch (Throwable var12) {
            var13.addSuppressed(var12);
         }

         throw var13;
      }

      input.close();
      if (total != declaredSize) {
         throw new IOException("Provider artifact size changed during validation");
      } else {
         return new ProviderArtifactStagingService.DigestMetadata(total, HexFormat.of().formatHex(digest.digest()));
      }
   }

   static long copyWithLimit(InputStream input, OutputStream output, long maxBytes) throws IOException {
      byte[] buffer = new byte[65536];
      long total = 0L;

      while (true) {
         int read = input.read(buffer);
         if (read < 0) {
            return total;
         }

         if (read != 0) {
            if (total > maxBytes - read) {
               throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider artifact exceeds the configured byte limit");
            }

            output.write(buffer, 0, read);
            total += read;
         }
      }
   }

   private String normalizeMime(String mimeType) {
      return mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
   }

   private void setDirectoryPermissions(Path path) throws IOException {
      try {
         Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS);
      } catch (UnsupportedOperationException var3) {
      }
   }

   private void setFilePermissions(Path path) throws IOException {
      try {
         Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
      } catch (UnsupportedOperationException var3) {
      }
   }

   private void deleteQuietly(Path path) {
      if (path != null) {
         try {
            Files.deleteIfExists(path);
         } catch (IOException var3) {
         }
      }
   }

   private ProviderExecutionException invalidContract(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, message);
   }

   public ProviderArtifactStagingService(
      final CreationProviderProperties properties, final ImageContentValidator imageContentValidator, final AudioOutputValidator audioOutputValidator
   ) {
      this.properties = properties;
      this.imageContentValidator = imageContentValidator;
      this.audioOutputValidator = audioOutputValidator;
   }

   @FunctionalInterface
   private interface BinaryValidator {
      ProviderArtifactStagingService.ValidatedBinary validate(Path partial) throws Exception;
   }

   private record DigestMetadata(long byteLength, String sha256) {
   }

   private record ValidatedBinary(String mimeType, String extension, Integer width, Integer height) {
   }
}

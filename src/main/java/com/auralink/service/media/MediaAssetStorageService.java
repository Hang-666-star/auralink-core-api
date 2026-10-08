package com.auralink.service.media;

import com.auralink.config.properties.MediaAssetProperties;
import com.auralink.entity.MediaAsset;
import com.auralink.exception.InvalidStoragePathException;
import com.auralink.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

@Service
public class MediaAssetStorageService {
   private static final int BUFFER_SIZE = 65536;
   private static final String STAGING_DIRECTORY = ".staging";
   private final MediaAssetProperties properties;
   private final MediaAssetStorageResolver resolver;

   public MediaAssetStorageService.StagedMediaFile stageUserUpload(InputStream input) {
      return this.stage(input, this.properties.getMaxUploadBytes());
   }

   public MediaAssetStorageService.StagedMediaFile stageGenerated(InputStream input) {
      return this.stage(input, this.properties.getMaxGeneratedBytes());
   }

   public MediaAssetStorageService.StoredMediaFile commitManaged(MediaAssetStorageService.StagedMediaFile staged, String storageKey) {
      this.requireOwnedStagedFile(staged);
      Path target = this.resolver.resolveManagedForWrite(storageKey);

      try {
         Files.createDirectories(target.getParent());
         target = this.resolver.resolveManagedForWrite(storageKey);
         if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)) {
            try {
               Files.move(staged.path(), target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
               Files.move(staged.path(), target);
            }

            return new MediaAssetStorageService.StoredMediaFile(storageKey, staged.size(), staged.sha256());
         } else {
            throw new StorageException("Managed MediaAsset destination already exists");
         }
      } catch (StorageException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new StorageException("Managed MediaAsset could not be committed", exception);
      }
   }

   public MediaAssetStorageService.CatalogMediaFile inspectCatalogReference(String relativeName) {
      String storageKey = this.resolver.toCatalogStorageKey(relativeName);
      Path path = this.resolver.resolveCatalogForRead(storageKey);

      try {
         return new MediaAssetStorageService.CatalogMediaFile(storageKey, path, new FileSystemResource(path), Files.size(path), this.sha256(path));
      } catch (IOException exception) {
         throw new StorageException("Catalog MediaAsset could not be inspected", exception);
      }
   }

   public MediaAssetStorageService.MediaAssetStoredResource resolve(MediaAsset asset) {
      if (asset == null) {
         throw new InvalidStoragePathException("MediaAsset is required");
      }

      Path path = this.resolver.resolveForRead(asset.getSourceType(), asset.getStorageKey());

      try {
         return new MediaAssetStorageService.MediaAssetStoredResource(new FileSystemResource(path), Files.size(path));
      } catch (IOException exception) {
         throw new StorageException("MediaAsset resource could not be inspected", exception);
      }
   }

   public void deleteManaged(String storageKey) {
      Path candidate = this.resolver.resolveManagedForWrite(storageKey);

      try {
         if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate)) {
            if (Files.isSymbolicLink(candidate)) {
               throw new InvalidStoragePathException("Refusing to delete a symbolic-link MediaAsset");
            }

            Path canonical = this.resolver.resolveManagedForRead(storageKey);
            Files.deleteIfExists(canonical);
         }
      } catch (InvalidStoragePathException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new StorageException("Managed MediaAsset cleanup failed", exception);
      }
   }

   public void discardStaged(MediaAssetStorageService.StagedMediaFile staged) {
      if (staged != null) {
         this.requireOwnedStagedPath(staged.path(), false);

         try {
            Files.deleteIfExists(staged.path());
         } catch (IOException exception) {
            throw new StorageException("Staged MediaAsset cleanup failed", exception);
         }
      }
   }

   private MediaAssetStorageService.StagedMediaFile stage(InputStream input, long maxBytes) {
      if (input == null) {
         throw new IllegalArgumentException("MediaAsset input stream is required");
      }

      if (maxBytes <= 0L) {
         throw new IllegalArgumentException("MediaAsset byte limit is invalid");
      }

      Path temporary = null;

      try {
         Path stagingRoot = this.ensureStagingRoot();
         temporary = Files.createTempFile(stagingRoot, "asset-", ".tmp");
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         long total = 0L;

         try (
            InputStream source = new DigestInputStream(input, digest);
            OutputStream destination = Files.newOutputStream(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
         ) {
            byte[] buffer = new byte[65536];

            int read;
            while ((read = source.read(buffer)) >= 0) {
               if (read != 0) {
                  if (total > maxBytes - read) {
                     throw new MediaAssetSizeLimitException("MediaAsset exceeds the configured byte limit");
                  }

                  destination.write(buffer, 0, read);
                  total += read;
               }
            }
         }

         return new MediaAssetStorageService.StagedMediaFile(temporary, total, HexFormat.of().formatHex(digest.digest()));
      } catch (MediaAssetSizeLimitException exception) {
         this.deleteTemporaryQuietly(temporary);
         throw exception;
      } catch (IOException | NoSuchAlgorithmException exception) {
         this.deleteTemporaryQuietly(temporary);
         throw new StorageException("MediaAsset could not be staged", exception);
      } catch (RuntimeException exception) {
         this.deleteTemporaryQuietly(temporary);
         throw exception;
      }
   }

   private Path ensureStagingRoot() throws IOException {
      Path managedRoot = this.resolver.managedRoot();
      Files.createDirectories(managedRoot);
      Path realManagedRoot = managedRoot.toRealPath();
      Path stagingRoot = managedRoot.resolve(".staging").normalize();
      Files.createDirectories(stagingRoot);
      Path realStagingRoot = stagingRoot.toRealPath();
      if (!realStagingRoot.startsWith(realManagedRoot)) {
         throw new InvalidStoragePathException("MediaAsset staging directory escapes its root");
      } else {
         return realStagingRoot;
      }
   }

   private void requireOwnedStagedFile(MediaAssetStorageService.StagedMediaFile staged) {
      if (staged != null && staged.path() != null && staged.size() >= 0L && staged.sha256() != null) {
         this.requireOwnedStagedPath(staged.path(), true);

         try {
            if (Files.size(staged.path()) != staged.size()) {
               throw new StorageException("Staged MediaAsset size changed before commit");
            }
         } catch (IOException exception) {
            throw new StorageException("Staged MediaAsset could not be verified", exception);
         }
      } else {
         throw new IllegalArgumentException("Valid staged MediaAsset metadata is required");
      }
   }

   private void requireOwnedStagedPath(Path path, boolean requireFile) {
      try {
         Path stagingRoot = this.ensureStagingRoot();
         Path normalized = path.toAbsolutePath().normalize();
         if (!normalized.startsWith(stagingRoot)
            || normalized.getParent() == null
            || !normalized.getParent().equals(stagingRoot)
            || Files.isSymbolicLink(normalized)) {
            throw new InvalidStoragePathException("Staged MediaAsset does not belong to this storage root");
         }

         if (requireFile && !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidStoragePathException("Staged MediaAsset is unavailable");
         }
      } catch (InvalidStoragePathException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new InvalidStoragePathException("Staged MediaAsset containment could not be verified", exception);
      }
   }

   private String sha256(Path path) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");

         try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
         }

         return HexFormat.of().formatHex(digest.digest());
      } catch (IOException | NoSuchAlgorithmException exception) {
         throw new StorageException("MediaAsset digest could not be computed", exception);
      }
   }

   private void deleteTemporaryQuietly(Path path) {
      if (path != null) {
         try {
            Files.deleteIfExists(path);
         } catch (IOException var3) {
         }
      }
   }

   public MediaAssetStorageService(final MediaAssetProperties properties, final MediaAssetStorageResolver resolver) {
      this.properties = properties;
      this.resolver = resolver;
   }

   public record CatalogMediaFile(String storageKey, Path path, FileSystemResource resource, long size, String sha256) {
   }

   public record MediaAssetStoredResource(FileSystemResource resource, long contentLength) {
   }

   public record StagedMediaFile(Path path, long size, String sha256) {
   }

   public record StoredMediaFile(String storageKey, long size, String sha256) {
   }
}

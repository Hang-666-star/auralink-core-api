package com.auralink.service.media;

import com.auralink.config.properties.MediaAssetProperties;
import com.auralink.config.properties.PaintingProperties;
import com.auralink.exception.InvalidStoragePathException;
import com.auralink.media.MediaAssetValues;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetStorageResolver {
   public static final String CATALOG_PREFIX = "catalog/";
   public static final String MANAGED_PREFIX = "managed/";
   private final MediaAssetProperties mediaAssetProperties;
   private final PaintingProperties paintingProperties;

   public String toCatalogStorageKey(String relativeName) {
      Path relative = this.parseRelativePath(relativeName, "catalog resource name");
      String normalized = this.toLogicalPath(relative.normalize());
      if (!normalized.isBlank() && !normalized.equals(".")) {
         return "catalog/" + normalized;
      } else {
         throw this.invalid("Catalog resource name must identify a file");
      }
   }

   public Path resolveForRead(String sourceType, String storageKey) {
      String normalizedSource = MediaAssetValues.requireSupportedSourceType(sourceType);
      if ("CATALOG_REFERENCE".equals(normalizedSource)) {
         return this.resolveCatalogForRead(storageKey);
      } else if (!"USER_UPLOAD".equals(normalizedSource) && !"GENERATED".equals(normalizedSource) && !"LEGACY_IMPORT".equals(normalizedSource)) {
         throw this.invalid("MediaAsset source type has no storage family");
      } else {
         return this.resolveManagedForRead(storageKey);
      }
   }

   public Path resolveManagedForWrite(String storageKey) {
      Path relative = this.relativePart(storageKey, "managed/", "managed storage key");
      Path root = this.managedRoot();
      Path candidate = this.containedLexically(root, relative);
      this.verifyExistingAncestor(root, candidate);
      return candidate;
   }

   public Path resolveManagedForRead(String storageKey) {
      Path candidate = this.resolveManagedForWrite(storageKey);
      return this.requireCanonicalRegularFile(this.managedRoot(), candidate);
   }

   public Path resolveCatalogForRead(String storageKey) {
      Path relative = this.relativePart(storageKey, "catalog/", "catalog storage key");
      Path root = this.catalogRoot();
      Path candidate = this.containedLexically(root, relative);
      return this.requireCanonicalRegularFile(root, candidate);
   }

   public Path managedRoot() {
      return this.configuredRoot(this.mediaAssetProperties.getManagedDir(), "managed MediaAsset root");
   }

   public Path catalogRoot() {
      return this.configuredRoot(this.paintingProperties.getPictureDir(), "catalog picture root");
   }

   private Path relativePart(String storageKey, String requiredPrefix, String label) {
      if (storageKey != null && storageKey.startsWith(requiredPrefix)) {
         String value = storageKey.substring(requiredPrefix.length());
         Path relative = this.parseRelativePath(value, label);
         if (relative.getNameCount() != 0 && !this.toLogicalPath(relative).equals(".")) {
            return relative;
         } else {
            throw this.invalid("MediaAsset storage key must identify a file");
         }
      } else {
         throw this.invalid("MediaAsset storage key does not match its storage family");
      }
   }

   private Path parseRelativePath(String value, String label) {
      if (value == null || value.isBlank() || this.containsControl(value)) {
         throw this.invalid(label + " is empty or invalid");
      }

      if (value.indexOf(92) < 0 && !value.startsWith("/") && !value.startsWith("//") && !value.matches("^[A-Za-z]:.*")) {
         Path path;
         try {
            path = Path.of(value);
         } catch (InvalidPathException exception) {
            throw new InvalidStoragePathException("MediaAsset storage key is invalid", exception);
         }

         if (path.isAbsolute()) {
            throw this.invalid(label + " must be relative");
         }

         for (Path segment : path) {
            String name = segment.toString();
            if (name.equals(".") || name.equals("..") || name.isBlank()) {
               throw this.invalid(label + " contains a forbidden path segment");
            }
         }

         return path;
      } else {
         throw this.invalid(label + " must use a relative logical path");
      }
   }

   private Path configuredRoot(String configured, String label) {
      if (configured != null && !configured.isBlank() && !this.containsControl(configured)) {
         try {
            return Path.of(configured).toAbsolutePath().normalize();
         } catch (InvalidPathException exception) {
            throw new InvalidStoragePathException(label + " is invalid", exception);
         }
      } else {
         throw this.invalid(label + " is not configured");
      }
   }

   private Path containedLexically(Path root, Path relative) {
      Path candidate = root.resolve(relative).normalize();
      if (!candidate.startsWith(root)) {
         throw this.invalid("MediaAsset storage key escapes its configured root");
      } else {
         return candidate;
      }
   }

   private void verifyExistingAncestor(Path root, Path candidate) {
      if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
         try {
            Path realRoot = root.toRealPath();
            Path existing = candidate;

            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
               existing = existing.getParent();
            }

            if (existing == null || !existing.toRealPath().startsWith(realRoot)) {
               throw this.invalid("MediaAsset path escapes its root through a symbolic link");
            }
         } catch (InvalidStoragePathException exception) {
            throw exception;
         } catch (IOException exception) {
            throw new InvalidStoragePathException("MediaAsset path containment could not be verified", exception);
         }
      }
   }

   private Path requireCanonicalRegularFile(Path root, Path candidate) {
      try {
         if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw this.invalid("MediaAsset storage root is unavailable");
         } else {
            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
               throw this.invalid("MediaAsset path escapes its root through a symbolic link");
            } else if (Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(realCandidate)) {
               return realCandidate;
            } else {
               throw this.invalid("MediaAsset resource is unavailable");
            }
         }
      } catch (InvalidStoragePathException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new InvalidStoragePathException("MediaAsset resource is unavailable", exception);
      }
   }

   private boolean containsControl(String value) {
      return value.codePoints().anyMatch(character -> Character.isISOControl(character));
   }

   private String toLogicalPath(Path path) {
      return path.toString().replace('\\', '/');
   }

   private InvalidStoragePathException invalid(String message) {
      return new InvalidStoragePathException(message);
   }

   public MediaAssetStorageResolver(final MediaAssetProperties mediaAssetProperties, final PaintingProperties paintingProperties) {
      this.mediaAssetProperties = mediaAssetProperties;
      this.paintingProperties = paintingProperties;
   }
}

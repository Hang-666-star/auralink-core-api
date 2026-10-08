package com.auralink.catalogcritic;

import com.auralink.exception.InvalidStoragePathException;
import com.auralink.exception.StorageException;
import com.auralink.service.media.MediaAssetSizeLimitException;
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
import org.springframework.stereotype.Service;

@Service
public class PostgresPrivateMediaStorageService {
   private static final int BUFFER_SIZE = 65536;
   private static final String STAGING_DIRECTORY = ".staging";
   private static final String PRIVATE_PREFIX = "private/";
   private final CatalogCriticProperties properties;

   public PostgresPrivateMediaStorageService(CatalogCriticProperties properties) {
      this.properties = properties;
   }

   public PostgresPrivateMediaStorageService.StagedPrivateFile stageVerifiedCopy(Path source, long expectedSize, String expectedSha256, long maximumBytes) {
      if (source != null && expectedSize >= 0L && expectedSha256 != null && expectedSha256.matches("[0-9a-f]{64}")) {
         if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new StorageException("Verified MediaAsset staging file is unavailable");
         }

         if (maximumBytes > 0L && expectedSize <= maximumBytes) {
            Path temporary = null;

            try {
               Path staging = this.stagingRoot();
               temporary = Files.createTempFile(staging, "asset-", ".tmp");
               MessageDigest digest = MessageDigest.getInstance("SHA-256");
               long total = 0L;
               InputStream input = new DigestInputStream(Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS), digest);

               try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                  byte[] buffer = new byte[65536];

                  int read;
                  while ((read = input.read(buffer)) >= 0) {
                     if (read != 0) {
                        if (total > maximumBytes - read) {
                           throw new MediaAssetSizeLimitException("MediaAsset exceeds the configured byte limit");
                        }

                        output.write(buffer, 0, read);
                        total += read;
                     }
                  }
               } catch (Throwable var19) {
                  try {
                     input.close();
                  } catch (Throwable var16) {
                     var19.addSuppressed(var16);
                  }

                  throw var19;
               }

               input.close();
               String var22 = HexFormat.of().formatHex(digest.digest());
               if (total == expectedSize && var22.equals(expectedSha256)) {
                  return new PostgresPrivateMediaStorageService.StagedPrivateFile(temporary, total, var22);
               } else {
                  throw new StorageException("Staged MediaAsset changed before private-media finalization");
               }
            } catch (MediaAssetSizeLimitException | StorageException exception) {
               this.deleteTemporaryQuietly(temporary);
               throw exception;
            } catch (IOException | NoSuchAlgorithmException exception) {
               this.deleteTemporaryQuietly(temporary);
               throw new StorageException("Private MediaAsset could not be staged", exception);
            }
         } else {
            throw new MediaAssetSizeLimitException("MediaAsset exceeds the configured byte limit");
         }
      } else {
         throw new IllegalArgumentException("A verified staged MediaAsset is required");
      }
   }

   public void commit(PostgresPrivateMediaStorageService.StagedPrivateFile staged, String storageKey) {
      this.requireOwnedStaged(staged);
      Path target = this.resolveForWrite(storageKey);

      try {
         Files.createDirectories(target.getParent());
         target = this.resolveForWrite(storageKey);
         if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)) {
            try {
               Files.move(staged.path(), target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
               Files.move(staged.path(), target);
            }
         } else {
            throw new StorageException("Private MediaAsset destination already exists");
         }
      } catch (StorageException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new StorageException("Private MediaAsset could not be finalized", exception);
      }
   }

   public void deleteFinal(String storageKey) {
      Path target = this.resolveForWrite(storageKey);

      try {
         if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            if (!Files.isSymbolicLink(target) && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
               Path root = this.privateRoot();
               Path real = target.toRealPath(LinkOption.NOFOLLOW_LINKS);
               if (!real.startsWith(root)) {
                  throw new InvalidStoragePathException("Private MediaAsset cleanup escapes its root");
               }

               Files.deleteIfExists(real);
            } else {
               throw new InvalidStoragePathException("Refusing to clean an unsafe private MediaAsset path");
            }
         }
      } catch (InvalidStoragePathException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new StorageException("Private MediaAsset cleanup failed", exception);
      }
   }

   public void discard(PostgresPrivateMediaStorageService.StagedPrivateFile staged) {
      if (staged != null) {
         this.requireOwnedStaged(staged);

         try {
            Files.deleteIfExists(staged.path());
         } catch (IOException exception) {
            throw new StorageException("Private MediaAsset staging cleanup failed", exception);
         }
      }
   }

   private Path privateRoot() throws IOException {
      String configured = this.properties.getPrivateMediaRoot();
      if (configured != null && !configured.isBlank() && !configured.codePoints().anyMatch(Character::isISOControl)) {
         Path root = Path.of(configured).toAbsolutePath().normalize();
         if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidStoragePathException("Private MediaAsset root is not a directory");
         } else {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) {
               throw new InvalidStoragePathException("Private MediaAsset root must not be a symbolic link");
            } else {
               Path real = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
               if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
                  throw new InvalidStoragePathException("Private MediaAsset root is unavailable");
               } else {
                  return real;
               }
            }
         }
      } else {
         throw new InvalidStoragePathException("Private MediaAsset root is not configured");
      }
   }

   private Path stagingRoot() throws IOException {
      Path root = this.privateRoot();
      Path staging = root.resolve(".staging").normalize();
      if (!staging.startsWith(root)) {
         throw new InvalidStoragePathException("Private MediaAsset staging root escapes its root");
      } else {
         Files.createDirectories(staging);
         if (Files.isSymbolicLink(staging)) {
            throw new InvalidStoragePathException("Private MediaAsset staging root must not be a symbolic link");
         } else {
            Path real = staging.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (real.startsWith(root) && Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
               return real;
            } else {
               throw new InvalidStoragePathException("Private MediaAsset staging root is unavailable");
            }
         }
      }
   }

   private Path resolveForWrite(String storageKey) {
      if (storageKey != null && storageKey.startsWith("private/") && !storageKey.contains("\\") && !storageKey.codePoints().anyMatch(Character::isISOControl)) {
         String rawRelative = storageKey.substring("private/".length());
         if (!rawRelative.isBlank() && !rawRelative.startsWith("/")) {
            Path relative = Path.of(storageKey).normalize();
            if (!relative.isAbsolute() && !relative.startsWith("..") && relative.getNameCount() >= 2) {
               for (Path segment : relative) {
                  if (segment.toString().equals(".") || segment.toString().equals("..") || segment.toString().isBlank()) {
                     throw new InvalidStoragePathException("Private MediaAsset storage key is invalid");
                  }
               }

               try {
                  Path root = this.privateRoot();
                  Path target = root.resolve(relative).normalize();
                  if (!target.startsWith(root)) {
                     throw new InvalidStoragePathException("Private MediaAsset storage key escapes its root");
                  }

                  this.verifyExistingAncestor(root, target);
                  return target;
               } catch (InvalidStoragePathException exception) {
                  throw exception;
               } catch (IOException exception) {
                  throw new InvalidStoragePathException("Private MediaAsset path containment could not be verified", exception);
               }
            } else {
               throw new InvalidStoragePathException("Private MediaAsset storage key escapes its root");
            }
         } else {
            throw new InvalidStoragePathException("Private MediaAsset storage key is invalid");
         }
      } else {
         throw new InvalidStoragePathException("Private MediaAsset storage key is invalid");
      }
   }

   private void verifyExistingAncestor(Path root, Path target) throws IOException {
      Path existing = target;

      while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
         existing = existing.getParent();
      }

      if (existing != null && !Files.isSymbolicLink(existing)) {
         Path realExisting = existing.toRealPath(LinkOption.NOFOLLOW_LINKS);
         if (!realExisting.startsWith(root)) {
            throw new InvalidStoragePathException("Private MediaAsset path escapes through an ancestor");
         }
      } else {
         throw new InvalidStoragePathException("Private MediaAsset path has an unsafe ancestor");
      }
   }

   private void requireOwnedStaged(PostgresPrivateMediaStorageService.StagedPrivateFile staged) {
      if (staged != null && staged.path() != null && staged.size() >= 0L && staged.sha256() != null && staged.sha256().matches("[0-9a-f]{64}")) {
         try {
            Path staging = this.stagingRoot();
            Path path = staged.path().toAbsolutePath().normalize();
            if (!path.startsWith(staging)
               || !staging.equals(path.getParent())
               || Files.isSymbolicLink(path)
               || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
               || Files.size(path) != staged.size()) {
               throw new InvalidStoragePathException("Private MediaAsset staging file is unavailable");
            }
         } catch (InvalidStoragePathException exception) {
            throw exception;
         } catch (IOException exception) {
            throw new InvalidStoragePathException("Private MediaAsset staging containment could not be verified", exception);
         }
      } else {
         throw new IllegalArgumentException("Valid private MediaAsset staging metadata is required");
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

   public record StagedPrivateFile(Path path, long size, String sha256) {
   }
}

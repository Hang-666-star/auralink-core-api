package com.auralink.catalogcritic;

import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.entity.User;
import com.auralink.security.access.MediaAssetAccessPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.core.io.FileSystemResource;

public class CatalogPrivateMediaService {
   private final PostgresCatalogCriticStore store;
   private final CatalogCriticProperties properties;

   CatalogPrivateMediaService(PostgresCatalogCriticStore store, CatalogCriticProperties properties) {
      this.store = store;
      this.properties = properties;
   }

   public CatalogPrivateMediaAsset accessible(String assetId, User currentUser) {
      CatalogPrivateMediaAsset asset = this.store.findPrivateAsset(assetId).orElseThrow(MediaAssetAccessPolicy::assetNotFound);
      if (!"ACTIVE".equals(asset.status())) {
         throw MediaAssetAccessPolicy.assetNotFound();
      } else if ("PUBLIC".equals(asset.visibility())) {
         return asset;
      } else if (currentUser != null && currentUser.getId() != null && currentUser.getId() == asset.ownerUserId()) {
         return asset;
      } else {
         throw MediaAssetAccessPolicy.assetNotFound();
      }
   }

   public boolean owns(String assetId) {
      return assetId != null && this.store.findPrivateAsset(assetId).isPresent();
   }

   public CatalogPrivateMediaService.AccessibleContent content(String assetId, User currentUser) {
      CatalogPrivateMediaAsset asset = this.accessible(assetId, currentUser);

      try {
         Path root = this.containedRoot();
         Path relative = relativeKey(asset.storageKey());
         Path candidate = root.resolve(relative).normalize();
         if (candidate.startsWith(root) && !Files.isSymbolicLink(candidate) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path resolved = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (resolved.startsWith(root)
               && Files.isReadable(resolved)
               && Files.size(resolved) == asset.fileSize()
               && sha256(resolved).equalsIgnoreCase(asset.contentSha256())) {
               return new CatalogPrivateMediaService.AccessibleContent(asset, new FileSystemResource(resolved), Files.size(resolved));
            } else {
               throw MediaAssetAccessPolicy.assetNotFound();
            }
         } else {
            throw MediaAssetAccessPolicy.assetNotFound();
         }
      } catch (ApiV1Exception exception) {
         throw exception;
      } catch (IOException | RuntimeException exception) {
         throw MediaAssetAccessPolicy.assetNotFound();
      }
   }

   private Path containedRoot() throws IOException {
      if (this.properties.getPrivateMediaRoot() != null && !this.properties.getPrivateMediaRoot().isBlank()) {
         Path root = Path.of(this.properties.getPrivateMediaRoot()).toRealPath(LinkOption.NOFOLLOW_LINKS);
         if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw MediaAssetAccessPolicy.assetNotFound();
         } else {
            return root;
         }
      } else {
         throw MediaAssetAccessPolicy.assetNotFound();
      }
   }

   private static Path relativeKey(String storageKey) {
      if (storageKey != null && !storageKey.isBlank() && !storageKey.startsWith("/") && !storageKey.contains("\\")) {
         Path relative = Path.of(storageKey).normalize();
         if (!relative.isAbsolute() && !relative.startsWith("..") && relative.getNameCount() != 0) {
            for (Path segment : relative) {
               if (segment.toString().equals(".") || segment.toString().equals("..")) {
                  throw MediaAssetAccessPolicy.assetNotFound();
               }
            }

            return relative;
         } else {
            throw MediaAssetAccessPolicy.assetNotFound();
         }
      } else {
         throw MediaAssetAccessPolicy.assetNotFound();
      }
   }

   private static String sha256(Path path) throws IOException {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");

         try (InputStream input = Files.newInputStream(path)) {
            byte[] block = new byte[65536];

            int size;
            while ((size = input.read(block)) >= 0) {
               if (size > 0) {
                  digest.update(block, 0, size);
               }
            }
         }

         return HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
      } catch (NoSuchAlgorithmException exception) {
         throw new IllegalStateException(exception);
      }
   }

   public record AccessibleContent(CatalogPrivateMediaAsset asset, FileSystemResource resource, long size) {
   }
}

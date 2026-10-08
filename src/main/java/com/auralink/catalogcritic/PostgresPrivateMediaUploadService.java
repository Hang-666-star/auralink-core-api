package com.auralink.catalogcritic;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.MediaAssetProperties;
import com.auralink.entity.User;
import com.auralink.exception.StorageException;
import com.auralink.media.MediaAssetValues;
import com.auralink.service.CurrentUserService;
import com.auralink.service.media.ImageContentValidator;
import com.auralink.service.media.InvalidImageContentException;
import com.auralink.service.media.MediaAssetSizeLimitException;
import com.auralink.service.media.MediaAssetStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

public class PostgresPrivateMediaUploadService {
   private static final DateTimeFormatter STORAGE_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
   private final CurrentUserService users;
   private final MediaAssetProperties mediaProperties;
   private final MediaAssetStorageService managedStorage;
   private final PostgresPrivateMediaStorageService privateStorage;
   private final ImageContentValidator images;
   private final PostgresPrivateMediaUploadStore store;

   PostgresPrivateMediaUploadService(
      CurrentUserService users,
      MediaAssetProperties mediaProperties,
      MediaAssetStorageService managedStorage,
      PostgresPrivateMediaStorageService privateStorage,
      ImageContentValidator images,
      PostgresPrivateMediaUploadStore store
   ) {
      this.users = users;
      this.mediaProperties = mediaProperties;
      this.managedStorage = managedStorage;
      this.privateStorage = privateStorage;
      this.images = images;
      this.store = store;
   }

   @Transactional(transactionManager = "catalogCriticTransactionManager")
   public CatalogPrivateMediaAsset storeAuthenticatedImage(MultipartFile file, String semanticType) {
      User owner = this.users.requireCurrentUser();
      if (owner.getId() == null) {
         throw new ApiV1Exception(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "需要身份验证");
      }

      String semantic = this.requireUploadSemanticType(semanticType);
      if (file != null && !file.isEmpty()) {
         if (file.getSize() > this.mediaProperties.getMaxUploadBytes()) {
            throw this.tooLarge();
         }

         MediaAssetStorageService.StagedMediaFile managedStaged = null;
         PostgresPrivateMediaStorageService.StagedPrivateFile privateStaged = null;
         String managedKey = null;
         String privateKey = null;
         boolean managedFinalized = false;
         boolean privateFinalized = false;
         boolean metadataPersisted = false;

         try (InputStream input = file.getInputStream()) {
            managedStaged = this.managedStorage.stageUserUpload(input);
            ImageContentValidator.ValidatedImage image = this.images.validateUpload(managedStaged.path(), file.getContentType(), file.getOriginalFilename());
            long verifiedSize = managedStaged.size();
            String verifiedSha256 = managedStaged.sha256();
            String publicId = UUID.randomUUID().toString();
            String filename = this.safeOriginalFilename(file.getOriginalFilename(), publicId, image.fileExtension());
            managedKey = this.managedKey(owner.getId(), publicId, image.fileExtension());
            privateKey = this.privateKey(owner.getId(), publicId, image.fileExtension());
            privateStaged = this.privateStorage
               .stageVerifiedCopy(managedStaged.path(), managedStaged.size(), managedStaged.sha256(), this.mediaProperties.getMaxUploadBytes());
            this.privateStorage.commit(privateStaged, privateKey);
            privateStaged = null;
            privateFinalized = true;
            this.managedStorage.commitManaged(managedStaged, managedKey);
            managedStaged = null;
            managedFinalized = true;
            LocalDateTime now = LocalDateTime.now();
            CatalogPrivateMediaAsset asset = new CatalogPrivateMediaAsset(
               publicId,
               owner.getId(),
               privateKey,
               filename,
               image.mimeType(),
               verifiedSize,
               verifiedSha256,
               image.width(),
               image.height(),
               null,
               "IMAGE",
               semantic,
               "USER_UPLOAD",
               "PRIVATE",
               "ACTIVE",
               now,
               now
            );
            this.store.insert(asset, managedKey);
            metadataPersisted = true;
            return asset;
         } catch (MediaAssetSizeLimitException exception) {
            throw this.tooLarge();
         } catch (InvalidImageContentException exception) {
            throw this.invalidImage("图片内容无效或格式不受支持");
         } catch (IOException | StorageException | DataAccessException exception) {
            throw this.storageUnavailable();
         } finally {
            if (managedStaged != null) {
               this.discardManaged(managedStaged);
            }

            if (privateStaged != null) {
               this.discardPrivate(privateStaged);
            }

            if (managedFinalized || privateFinalized) {
               this.cleanupOnRollbackOrFailure(managedKey, privateKey, managedFinalized, privateFinalized, metadataPersisted);
            }
         }
      } else {
         throw this.invalidImage("上传图片不能为空");
      }
   }

   private String requireUploadSemanticType(String value) {
      try {
         return MediaAssetValues.requireUploadSemanticType(value != null && !value.isBlank() ? value : "IMAGE");
      } catch (IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_ASSET_TYPE, "上传图片语义类型不受支持");
      }
   }

   private String managedKey(long ownerId, String publicId, String extension) {
      return "managed/private/" + ownerId + "/" + YearMonth.now().format(STORAGE_MONTH) + "/" + publicId + "." + extension;
   }

   private String privateKey(long ownerId, String publicId, String extension) {
      return "private/" + ownerId + "/" + YearMonth.now().format(STORAGE_MONTH) + "/" + publicId + "." + extension;
   }

   private String safeOriginalFilename(String supplied, String publicId, String extension) {
      if (supplied == null) {
         return publicId + "." + extension;
      } else {
         String basename = supplied.replace('\\', '/');
         basename = basename.substring(basename.lastIndexOf(47) + 1).replaceAll("[\\p{Cntrl}]", "").trim();
         if (!basename.isBlank() && !".".equals(basename) && !"..".equals(basename)) {
            return basename.length() <= 512 ? basename : basename.substring(0, 512);
         } else {
            return publicId + "." + extension;
         }
      }
   }

   private void cleanupOnRollbackOrFailure(String managedKey, String privateKey, boolean managed, boolean privateCopy, boolean metadataPersisted) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
         TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) {
               if (status != 0) {
                  PostgresPrivateMediaUploadService.this.cleanup(managedKey, privateKey, managed, privateCopy);
               }
            }
         });
      } else if (!metadataPersisted) {
         this.cleanup(managedKey, privateKey, managed, privateCopy);
      }
   }

   private void cleanup(String managedKey, String privateKey, boolean managed, boolean privateCopy) {
      if (managed && managedKey != null) {
         try {
            this.managedStorage.deleteManaged(managedKey);
         } catch (RuntimeException var7) {
         }
      }

      if (privateCopy && privateKey != null) {
         try {
            this.privateStorage.deleteFinal(privateKey);
         } catch (RuntimeException var6) {
         }
      }
   }

   private void discardManaged(MediaAssetStorageService.StagedMediaFile staged) {
      try {
         this.managedStorage.discardStaged(staged);
      } catch (RuntimeException var3) {
      }
   }

   private void discardPrivate(PostgresPrivateMediaStorageService.StagedPrivateFile staged) {
      try {
         this.privateStorage.discard(staged);
      } catch (RuntimeException var3) {
      }
   }

   private ApiV1Exception invalidImage(String message) {
      return new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_IMAGE, message);
   }

   private ApiV1Exception tooLarge() {
      return new ApiV1Exception(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.ASSET_TOO_LARGE, "上传图片超过大小限制");
   }

   private ApiV1Exception storageUnavailable() {
      return new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CATALOG_READ_UNAVAILABLE, "私有媒体暂时不可用");
   }
}

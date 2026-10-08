package com.auralink.service.media;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.MediaAssetProperties;
import com.auralink.entity.MediaAsset;
import com.auralink.entity.User;
import com.auralink.exception.StorageException;
import com.auralink.media.MediaAssetValues;
import com.auralink.repository.MediaAssetRepository;
import com.auralink.repository.UserRepository;
import com.auralink.security.access.MediaAssetAccessPolicy;
import com.auralink.service.CurrentUserService;
import java.io.IOException;
import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaAssetService {
   private static final Logger log = LoggerFactory.getLogger(MediaAssetService.class);
   private static final DateTimeFormatter STORAGE_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
   private static final Pattern SAFE_MIME_TYPE = Pattern.compile("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+");
   private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
   private static final Map<String, String> MIME_EXTENSIONS = Map.ofEntries(
      Map.entry("image/jpeg", "jpg"),
      Map.entry("image/png", "png"),
      Map.entry("audio/mpeg", "mp3"),
      Map.entry("audio/wav", "wav"),
      Map.entry("audio/x-wav", "wav"),
      Map.entry("audio/flac", "flac"),
      Map.entry("audio/ogg", "ogg"),
      Map.entry("audio/mp4", "m4a"),
      Map.entry("video/mp4", "mp4"),
      Map.entry("video/webm", "webm"),
      Map.entry("video/quicktime", "mov"),
      Map.entry("application/json", "json"),
      Map.entry("text/plain", "txt"),
      Map.entry("application/octet-stream", "bin")
   );
   private final MediaAssetRepository mediaAssetRepository;
   private final UserRepository userRepository;
   private final CurrentUserService currentUserService;
   private final MediaAssetAccessPolicy accessPolicy;
   private final MediaAssetStorageService storageService;
   private final ImageContentValidator imageValidator;
   private final MediaAssetProperties properties;

   @Transactional
   public MediaAsset storeAuthenticatedImage(MultipartFile file, String semanticType) {
      User owner = this.currentUserService.requireCurrentUser();
      String normalizedSemantic = this.normalizeUploadSemanticType(semanticType);
      if (file != null && !file.isEmpty()) {
         if (file.getSize() > this.properties.getMaxUploadBytes()) {
            throw this.assetTooLarge();
         }

         MediaAssetStorageService.StagedMediaFile staged = null;
         String finalStorageKey = null;

         try (InputStream input = file.getInputStream()) {
            staged = this.storageService.stageUserUpload(input);
            ImageContentValidator.ValidatedImage image = this.imageValidator.validateUpload(staged.path(), file.getContentType(), file.getOriginalFilename());
            String publicId = UUID.randomUUID().toString();
            finalStorageKey = this.managedStorageKey(owner, publicId, image.fileExtension());
            MediaAssetStorageService.StoredMediaFile stored = this.storageService.commitManaged(staged, finalStorageKey);
            staged = null;
            MediaAsset asset = MediaAsset.builder()
               .publicId(publicId)
               .ownerUser(owner)
               .storageKey(stored.storageKey())
               .originalFilename(this.safeOriginalFilename(file.getOriginalFilename(), publicId, image.fileExtension()))
               .mimeType(image.mimeType())
               .fileSize(stored.size())
               .sha256(stored.sha256())
               .width(image.width())
               .height(image.height())
               .assetType("IMAGE")
               .semanticType(normalizedSemantic)
               .sourceType("USER_UPLOAD")
               .visibility("PRIVATE")
               .status("ACTIVE")
               .build();
            return this.persistManagedAsset(asset, finalStorageKey);
         } catch (MediaAssetSizeLimitException exception) {
            throw this.assetTooLarge();
         } catch (InvalidImageContentException exception) {
            throw this.invalidImage("图片内容无效或格式不受支持");
         } catch (IOException exception) {
            throw this.assetStorageError(exception);
         } catch (StorageException exception) {
            throw this.assetStorageError(exception);
         } finally {
            if (staged != null) {
               this.storageService.discardStaged(staged);
            }
         }
      } else {
         throw this.invalidImage("上传图片不能为空");
      }
   }

   @Transactional(readOnly = true)
   public MediaAsset getAccessibleAsset(String assetId) {
      String publicId = this.requireCanonicalUuid(assetId);
      MediaAsset asset = this.mediaAssetRepository.findByPublicId(publicId).orElseThrow(MediaAssetAccessPolicy::assetNotFound);
      User currentUser = this.currentUserService.findCurrentUser().orElse(null);
      this.accessPolicy.requireReadable(asset, currentUser);
      return asset;
   }

   @Transactional(readOnly = true)
   public MediaAssetService.AccessibleMediaAssetContent getAccessibleContent(String assetId) {
      MediaAsset asset = this.getAccessibleAsset(assetId);

      try {
         MediaAssetStorageService.MediaAssetStoredResource stored = this.storageService.resolve(asset);
         FileSystemResource resource = stored.resource();
         if (resource.exists() && resource.isReadable() && stored.contentLength() >= 0L) {
            return new MediaAssetService.AccessibleMediaAssetContent(asset, resource, stored.contentLength());
         } else {
            throw MediaAssetAccessPolicy.assetNotFound();
         }
      } catch (ApiV1Exception exception) {
         throw exception;
      } catch (StorageException exception) {
         throw MediaAssetAccessPolicy.assetNotFound();
      }
   }

   @Transactional
   public MediaAsset registerCatalogReference(String relativeName) {
      MediaAssetStorageService.CatalogMediaFile catalogFile = this.storageService.inspectCatalogReference(relativeName);
      ImageContentValidator.ValidatedImage image = this.imageValidator.validateTrustedImage(catalogFile.path());
      MediaAsset existing = this.mediaAssetRepository.findByStorageKey(catalogFile.storageKey()).orElse(null);
      if (existing != null) {
         if (!"CATALOG_REFERENCE".equals(existing.getSourceType())) {
            throw new StorageException("Catalog storage key is already assigned to another source family");
         }

         existing.setOwnerUser(null);
         existing.setOriginalFilename(this.safeOriginalFilename(relativeName, existing.getPublicId(), image.fileExtension()));
         existing.setMimeType(image.mimeType());
         existing.setFileSize(catalogFile.size());
         existing.setSha256(catalogFile.sha256());
         existing.setWidth(image.width());
         existing.setHeight(image.height());
         existing.setDurationSeconds(null);
         existing.setAssetType("IMAGE");
         existing.setSemanticType("PAINTING");
         existing.setVisibility("PUBLIC");
         existing.setStatus("ACTIVE");
         return (MediaAsset)this.mediaAssetRepository.saveAndFlush(existing);
      } else {
         String publicId = UUID.randomUUID().toString();
         MediaAsset asset = MediaAsset.builder()
            .publicId(publicId)
            .ownerUser(null)
            .storageKey(catalogFile.storageKey())
            .originalFilename(this.safeOriginalFilename(relativeName, publicId, image.fileExtension()))
            .mimeType(image.mimeType())
            .fileSize(catalogFile.size())
            .sha256(catalogFile.sha256())
            .width(image.width())
            .height(image.height())
            .assetType("IMAGE")
            .semanticType("PAINTING")
            .sourceType("CATALOG_REFERENCE")
            .visibility("PUBLIC")
            .status("ACTIVE")
            .build();
         return (MediaAsset)this.mediaAssetRepository.saveAndFlush(asset);
      }
   }

   @Transactional
   public MediaAsset storeGeneratedAsset(GeneratedAssetRequest request) {
      if (request == null) {
         throw new IllegalArgumentException("Generated asset request is required");
      }

      User owner = this.requirePersistedOwner(request.owner());
      String assetType = this.requireGeneratedAssetType(request.assetType());
      String semanticType = this.requireGeneratedSemanticType(request.semanticType());
      this.validateDuration(request.durationSeconds());
      MediaAssetStorageService.StagedMediaFile staged = null;
      String finalStorageKey = null;

      try (InputStream input = request.content()) {
         staged = this.storageService.stageGenerated(input);
         if (staged.size() == 0L) {
            throw new IllegalArgumentException("Generated asset content must not be empty");
         }

         String publicId = UUID.randomUUID().toString();
         ImageContentValidator.ValidatedImage image = null;
         String mimeType;
         String extension;
         if ("IMAGE".equals(assetType)) {
            image = this.imageValidator.validateTrustedImage(staged.path());
            mimeType = image.mimeType();
            this.requireMatchingGeneratedImageMime(request.mimeType(), mimeType);
            extension = image.fileExtension();
         } else {
            mimeType = this.requireSafeMimeType(request.mimeType(), assetType);
            extension = this.safeGeneratedExtension(request.originalFilename(), mimeType, assetType);
         }

         finalStorageKey = this.managedStorageKey(owner, publicId, extension);
         MediaAssetStorageService.StoredMediaFile stored = this.storageService.commitManaged(staged, finalStorageKey);
         staged = null;
         MediaAsset asset = MediaAsset.builder()
            .publicId(publicId)
            .ownerUser(owner)
            .storageKey(stored.storageKey())
            .originalFilename(this.safeOriginalFilename(request.originalFilename(), publicId, extension))
            .mimeType(mimeType)
            .fileSize(stored.size())
            .sha256(stored.sha256())
            .width(image == null ? null : image.width())
            .height(image == null ? null : image.height())
            .durationSeconds(request.durationSeconds())
            .assetType(assetType)
            .semanticType(semanticType)
            .sourceType("GENERATED")
            .visibility("PRIVATE")
            .status("ACTIVE")
            .build();
         return this.persistManagedAsset(asset, finalStorageKey);
      } catch (MediaAssetSizeLimitException exception) {
         throw this.assetTooLarge();
      } catch (InvalidImageContentException exception) {
         throw this.invalidImage("生成的图片内容无效或格式不受支持");
      } catch (IOException exception) {
         throw this.assetStorageError(exception);
      } catch (StorageException exception) {
         throw this.assetStorageError(exception);
      } finally {
         if (staged != null) {
            this.storageService.discardStaged(staged);
         }
      }
   }

   private MediaAsset persistManagedAsset(MediaAsset asset, String storageKey) {
      this.registerRollbackCleanup(storageKey);

      try {
         return (MediaAsset)this.mediaAssetRepository.saveAndFlush(asset);
      } catch (DataAccessException exception) {
         this.safeDeleteManaged(storageKey);
         throw this.assetStorageError(exception);
      } catch (RuntimeException exception) {
         this.safeDeleteManaged(storageKey);
         throw exception;
      }
   }

   private void registerRollbackCleanup(String storageKey) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
         TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) {
               if (status != 0) {
                  MediaAssetService.this.safeDeleteManaged(storageKey);
               }
            }
         });
      }
   }

   private void safeDeleteManaged(String storageKey) {
      try {
         this.storageService.deleteManaged(storageKey);
      } catch (RuntimeException cleanupFailure) {
         log.warn("Unable to clean a newly stored MediaAsset after transaction rollback; type={}", cleanupFailure.getClass().getSimpleName());
      }
   }

   private User requirePersistedOwner(User owner) {
      if (owner != null && owner.getId() != null) {
         return (User)this.userRepository.findById(owner.getId()).orElseThrow(() -> new IllegalArgumentException("Generated asset owner does not exist"));
      } else {
         throw new IllegalArgumentException("Generated asset owner must be a persisted user");
      }
   }

   private String normalizeUploadSemanticType(String value) {
      try {
         return MediaAssetValues.requireUploadSemanticType(value != null && !value.isBlank() ? value : "IMAGE");
      } catch (IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_ASSET_TYPE, "上传图片语义类型不受支持");
      }
   }

   private String requireGeneratedAssetType(String value) {
      try {
         return MediaAssetValues.requireSupportedAssetType(value);
      } catch (IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_ASSET_TYPE, "生成资源类型不受支持");
      }
   }

   private String requireGeneratedSemanticType(String value) {
      try {
         return MediaAssetValues.requireSupportedSemanticType(value);
      } catch (IllegalArgumentException exception) {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_ASSET_TYPE, "生成资源语义类型不受支持");
      }
   }

   private String requireCanonicalUuid(String value) {
      if (value != null && !value.isBlank()) {
         try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value.toLowerCase(Locale.ROOT))) {
               throw this.invalidAssetId();
            } else {
               return canonical;
            }
         } catch (IllegalArgumentException exception) {
            throw this.invalidAssetId();
         }
      } else {
         throw this.invalidAssetId();
      }
   }

   private String managedStorageKey(User owner, String publicId, String extension) {
      return "managed/private/" + owner.getId() + "/" + YearMonth.now().format(STORAGE_MONTH) + "/" + publicId + "." + extension;
   }

   private String safeOriginalFilename(String supplied, String publicId, String extension) {
      if (supplied == null) {
         return publicId + "." + extension;
      }

      String basename = supplied.replace('\\', '/');
      basename = basename.substring(basename.lastIndexOf(47) + 1).replaceAll("[\\p{Cntrl}]", "").trim();
      if (!basename.isBlank() && !".".equals(basename) && !"..".equals(basename)) {
         if (basename.length() > 512) {
            basename = basename.substring(0, 512);
         }

         return basename;
      } else {
         return publicId + "." + extension;
      }
   }

   private String requireSafeMimeType(String supplied, String assetType) {
      String mimeType = supplied;
      if (mimeType == null || mimeType.isBlank()) {
         mimeType = switch (assetType) {
            case "AUDIO" -> "audio/mpeg";
            case "VIDEO" -> "video/mp4";
            default -> "application/octet-stream";
         };
      }

      mimeType = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if (mimeType.length() <= 255 && SAFE_MIME_TYPE.matcher(mimeType).matches()) {
         boolean typeMatches = switch (assetType) {
            case "AUDIO" -> mimeType.startsWith("audio/");
            case "VIDEO" -> mimeType.startsWith("video/");
            case "FILE" -> true;
            default -> false;
         };
         if (!typeMatches) {
            throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "生成资源媒体类型与资源类型不匹配");
         } else {
            return mimeType;
         }
      } else {
         throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "生成资源媒体类型无效");
      }
   }

   private void requireMatchingGeneratedImageMime(String supplied, String detected) {
      if (supplied != null && !supplied.isBlank()) {
         String normalized = supplied.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
         if (!detected.equals(normalized)) {
            throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "生成图片媒体类型与实际内容不匹配");
         }
      }
   }

   private String safeGeneratedExtension(String originalFilename, String mimeType, String assetType) {
      String mapped = MIME_EXTENSIONS.get(mimeType);
      if (mapped != null) {
         return mapped;
      }

      if (originalFilename != null) {
         String basename = originalFilename.replace('\\', '/');
         basename = basename.substring(basename.lastIndexOf(47) + 1);
         int dot = basename.lastIndexOf(46);
         if (dot >= 0 && dot < basename.length() - 1) {
            String extension = basename.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (SAFE_EXTENSION.matcher(extension).matches()) {
               return extension;
            }
         }
      }
      return switch (assetType) {
         case "AUDIO" -> "audio";
         case "VIDEO" -> "video";
         default -> "bin";
      };
   }

   private void validateDuration(Double durationSeconds) {
      if (durationSeconds != null && (!Double.isFinite(durationSeconds) || durationSeconds < 0.0)) {
         throw new IllegalArgumentException("Generated asset duration must be finite and non-negative");
      }
   }

   private ApiV1Exception invalidAssetId() {
      return new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_ASSET_ID, "资源标识格式无效");
   }

   private ApiV1Exception invalidImage(String message) {
      return new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_IMAGE, message);
   }

   private ApiV1Exception assetTooLarge() {
      return new ApiV1Exception(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.ASSET_TOO_LARGE, "资源大小超过允许上限");
   }

   private ApiV1Exception assetStorageError(Exception cause) {
      log.error("MediaAsset storage operation failed; type={}", cause.getClass().getSimpleName());
      return new ApiV1Exception(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.ASSET_STORAGE_ERROR, "资源存储失败");
   }

   public MediaAssetService(
      final MediaAssetRepository mediaAssetRepository,
      final UserRepository userRepository,
      final CurrentUserService currentUserService,
      final MediaAssetAccessPolicy accessPolicy,
      final MediaAssetStorageService storageService,
      final ImageContentValidator imageValidator,
      final MediaAssetProperties properties
   ) {
      this.mediaAssetRepository = mediaAssetRepository;
      this.userRepository = userRepository;
      this.currentUserService = currentUserService;
      this.accessPolicy = accessPolicy;
      this.storageService = storageService;
      this.imageValidator = imageValidator;
      this.properties = properties;
   }

   public record AccessibleMediaAssetContent(MediaAsset asset, FileSystemResource resource, long contentLength) {
   }
}

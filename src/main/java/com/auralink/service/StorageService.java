package com.auralink.service;

import com.auralink.config.properties.StorageProperties;
import com.auralink.exception.InvalidStoragePathException;
import com.auralink.exception.StorageException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {
   private static final Logger log = LoggerFactory.getLogger(StorageService.class);
   private final StorageProperties storageConfig;
   private final SafeRemoteResourceFetcher remoteResourceFetcher;
   private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));
   private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList("mp3", "wav", "flac", "aac", "ogg", "m4a"));
   private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList("mp4", "avi", "mov", "mkv", "webm", "flv"));
   private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = new HashSet<>(Arrays.asList("txt", "pdf", "doc", "docx", "json", "xml"));
   private static final Map<String, Set<String>> CONTENT_TYPE_MAPPING = new HashMap<>();

   public String storeFile(MultipartFile file) throws IOException {
      if (file.isEmpty()) {
         throw new IllegalArgumentException("无法存储空文件");
      }

      String originalFilename = file.getOriginalFilename();
      if (originalFilename != null && !originalFilename.isEmpty()) {
         String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
         if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension + "。请上传jpg、jpeg、png、gif、bmp或webp格式的图片");
         }

         String newFilename = UUID.randomUUID().toString() + "." + extension;

         try {
            Path uploadDir = Paths.get(this.storageConfig.getUploadDir());
            if (!Files.exists(uploadDir)) {
               Files.createDirectories(uploadDir);
               log.info("创建上传目录: {}", uploadDir);
            }

            Path targetPath = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已上传: {}, 原始文件名: {}, 大小: {} 字节", new Object[]{targetPath, originalFilename, file.getSize()});
            return targetPath.toAbsolutePath().toString();
         } catch (IOException e) {
            log.error("文件存储失败: type={}", e.getClass().getSimpleName());
            throw new StorageException("文件存储失败", e);
         }
      } else {
         throw new IllegalArgumentException("文件名为空");
      }
   }

   public File getFile(String filePath) {
      File file = new File(filePath);
      if (file.exists() && file.isFile()) {
         return file;
      }

      log.warn("请求的文件不存在: {}", filePath);
      return null;
   }

   public void cleanupOldFiles() {
      log.info("开始清理过期文件...");

      try {
         Path uploadDir = Paths.get(this.storageConfig.getUploadDir());
         LocalDateTime now = LocalDateTime.now();
         if (Files.exists(uploadDir)) {
            try (Stream<Path> files = Files.list(uploadDir)) {
               files.filter(x$0 -> Files.isRegularFile(x$0)).forEach(file -> {
                  try {
                     LocalDateTime fileCreated = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
                     long hoursBetween = ChronoUnit.HOURS.between(fileCreated, now);
                     if (hoursBetween > 24L) {
                        Files.delete(file);
                        log.info("已删除过期文件: {}", file);
                     }
                  } catch (IOException e) {
                     log.error("清理文件时出错: {}", e.getMessage());
                  }
               });
            }
         }
      } catch (IOException e) {
         log.error("清理过期文件出错: {}", e.getMessage());
      }
   }

   public String storeBase64File(String base64Data, String contentType, String fileExtension, Long userId) throws IOException {
      if (base64Data != null && !base64Data.trim().isEmpty()) {
         this.validateFileType(contentType, fileExtension);

         byte[] decodedBytes;
         try {
            String cleanBase64 = base64Data;
            if (base64Data.contains(",")) {
               cleanBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            decodedBytes = Base64.getDecoder().decode(cleanBase64);
         } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的Base64数据格式");
         }

         String filePath = this.generateFilePath(contentType, fileExtension, userId);
         Path targetPath = Paths.get(filePath);
         Files.createDirectories(targetPath.getParent());
         Files.write(targetPath, decodedBytes);
         log.info("Base64文件已保存: {}, 类型: {}, 大小: {} 字节", new Object[]{filePath, contentType, decodedBytes.length});
         return this.getRelativeFilePath(filePath);
      } else {
         throw new IllegalArgumentException("Base64数据不能为空");
      }
   }

   public String storeRemoteFile(String remoteUrl, String contentType, String fileExtension, Long userId) throws IOException {
      if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
         this.validateFileType(contentType, fileExtension);
         String filePath = this.generateFilePath(contentType, fileExtension, userId);
         Path targetPath = Paths.get(filePath);
         Files.createDirectories(targetPath.getParent());

         try {
            this.remoteResourceFetcher.fetchTo(remoteUrl, targetPath);
         } catch (IOException e) {
            throw new IOException("远程资源下载或安全校验失败", e);
         }

         log.info("远程文件已下载并保存: {}, 类型: {}", filePath, contentType);
         return this.getRelativeFilePath(filePath);
      } else {
         throw new IllegalArgumentException("远程URL不能为空");
      }
   }

   private void validateFileType(String contentType, String fileExtension) {
      if (contentType != null && !contentType.trim().isEmpty()) {
         Set<String> allowedExtensions = CONTENT_TYPE_MAPPING.get(contentType.toLowerCase());
         if (allowedExtensions == null) {
            throw new IllegalArgumentException("不支持的内容类型: " + contentType);
         }

         if (fileExtension != null && !fileExtension.trim().isEmpty()) {
            String cleanExtension = fileExtension.toLowerCase().replaceAll("^\\.", "");
            if (!allowedExtensions.contains(cleanExtension)) {
               throw new IllegalArgumentException("文件扩展名 " + fileExtension + " 与内容类型 " + contentType + " 不匹配");
            }
         }
      } else {
         throw new IllegalArgumentException("内容类型不能为空");
      }
   }

   private String generateFilePath(String contentType, String fileExtension, Long userId) {
      String extension = fileExtension != null && !fileExtension.trim().isEmpty()
         ? fileExtension.replaceAll("^\\.", "")
         : this.getDefaultExtension(contentType);
      String fileName = UUID.randomUUID().toString() + "." + extension;
      String monthFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
      return Paths.get(this.storageConfig.getUploadDir(), contentType.toLowerCase(), monthFolder, userId.toString(), fileName).toString();
   }

   private String getDefaultExtension(String contentType) {
      Set<String> extensions = CONTENT_TYPE_MAPPING.get(contentType.toLowerCase());
      return extensions != null && !extensions.isEmpty() ? extensions.iterator().next() : "bin";
   }

   private String getRelativeFilePath(String absolutePath) {
      Path storageRoot = this.getStorageRoot();
      Path storedPath = Paths.get(absolutePath).toAbsolutePath().normalize();
      if (storedPath.startsWith(storageRoot)) {
         return storageRoot.relativize(storedPath).toString().replace("\\", "/");
      } else {
         throw new InvalidStoragePathException("存储结果不在配置的存储目录中");
      }
   }

   public Path resolveStoredFile(String relativePath) {
      if (relativePath != null && !relativePath.isBlank() && relativePath.indexOf(0) < 0 && relativePath.indexOf(13) < 0 && relativePath.indexOf(10) < 0) {
         if (relativePath.indexOf(92) < 0 && !relativePath.startsWith("//") && !relativePath.matches("^[A-Za-z]:[/\\\\].*")) {
            Path requested;
            try {
               requested = Paths.get(relativePath);
            } catch (InvalidPathException e) {
               throw new InvalidStoragePathException("文件路径格式无效", e);
            }

            if (requested.isAbsolute()) {
               throw new InvalidStoragePathException("不允许绝对文件路径");
            }

            for (Path segment : requested) {
               if ("..".equals(segment.toString())) {
                  throw new InvalidStoragePathException("文件路径不得包含上级目录跳转");
               }
            }

            Path storageRoot = this.getStorageRoot();
            Path candidate = storageRoot.resolve(requested).normalize();
            if (!candidate.startsWith(storageRoot)) {
               throw new InvalidStoragePathException("文件路径超出配置的存储目录");
            } else {
               return this.resolveCanonicalContainedPath(storageRoot, candidate);
            }
         } else {
            throw new InvalidStoragePathException("不允许绝对路径或非标准路径分隔符");
         }
      } else {
         throw new InvalidStoragePathException("文件路径不能为空");
      }
   }

   private Path getStorageRoot() {
      return Paths.get(this.storageConfig.getUploadDir()).toAbsolutePath().normalize();
   }

   private Path resolveCanonicalContainedPath(Path storageRoot, Path candidate) {
      try {
         if (!Files.exists(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            return candidate;
         }

         Path realRoot = storageRoot.toRealPath();
         Path existing = candidate;

         while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
         }

         if (existing == null) {
            throw new InvalidStoragePathException("文件路径通过符号链接超出配置的存储目录");
         } else {
            Path realExisting = existing.toRealPath();
            if (!realExisting.startsWith(realRoot)) {
               throw new InvalidStoragePathException("文件路径通过符号链接超出配置的存储目录");
            } else {
               return Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) ? realExisting : candidate;
            }
         }
      } catch (InvalidStoragePathException e) {
         throw e;
      } catch (IOException e) {
         throw new InvalidStoragePathException("无法验证文件存储路径", e);
      }
   }

   public StorageService(final StorageProperties storageConfig, final SafeRemoteResourceFetcher remoteResourceFetcher) {
      this.storageConfig = storageConfig;
      this.remoteResourceFetcher = remoteResourceFetcher;
   }

   static {
      CONTENT_TYPE_MAPPING.put("image", ALLOWED_IMAGE_EXTENSIONS);
      CONTENT_TYPE_MAPPING.put("audio", ALLOWED_AUDIO_EXTENSIONS);
      CONTENT_TYPE_MAPPING.put("video", ALLOWED_VIDEO_EXTENSIONS);
      CONTENT_TYPE_MAPPING.put("document", ALLOWED_DOCUMENT_EXTENSIONS);
   }
}

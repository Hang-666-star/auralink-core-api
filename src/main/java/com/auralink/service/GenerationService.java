package com.auralink.service;

import com.auralink.config.properties.ServiceProperties;
import com.auralink.dto.GenerateMusicRequest;
import com.auralink.dto.ImageDescriptionRequest;
import com.auralink.dto.RecordApiUsageRequest;
import com.auralink.dto.UploadResultRequest;
import com.auralink.entity.GenerationLog;
import com.auralink.entity.User;
import com.auralink.repository.GenerationLogRepository;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class GenerationService {
   private static final Logger log = LoggerFactory.getLogger(GenerationService.class);
   private final RestTemplate restTemplate;
   private final ServiceProperties serviceConfig;
   private final GenerationLogRepository generationLogRepository;
   private final StorageService storageService;
   private boolean vmmServiceHealthy = false;
   private boolean nonvmmServiceHealthy = false;
   private long lastVmmHealthCheck = 0L;
   private long lastNonvmmHealthCheck = 0L;
   private static final long HEALTH_CHECK_INTERVAL = 60000L;

   public Map<String, Object> getModels() {
      Map<String, Object> result = new HashMap<>();
      Map<String, Object> vmmResult = new HashMap<>();
      Map<String, Object> nonVmmResult = new HashMap<>();

      try {
         boolean isVmmHealthy = this.checkVmmServiceHealth();
         vmmResult.put("available", isVmmHealthy);
         vmmResult.put("models", isVmmHealthy ? new String[]{"small"} : new String[0]);
         boolean isNonVmmHealthy = this.checkNonvmmServiceHealth();
         nonVmmResult.put("available", isNonVmmHealthy);
         nonVmmResult.put("models", isNonVmmHealthy ? new String[]{"small", "medium", "large"} : new String[0]);
      } catch (Exception e) {
         log.error("获取模型信息时出错: {}", e.getMessage());
         vmmResult.put("available", false);
         vmmResult.put("models", new String[0]);
         nonVmmResult.put("available", false);
         nonVmmResult.put("models", new String[0]);
      }

      result.put("vmm", vmmResult);
      result.put("nonvmm", nonVmmResult);
      return result;
   }

   public Map<String, Object> generateImageDescription(ImageDescriptionRequest request) {
      log.info("生成图像描述: {}", request.getImageUrl());
      User currentUser = this.getCurrentUser();
      Map<String, Object> requestData = new HashMap<>();
      requestData.put("image", request.getImageUrl());
      Map<String, Object> result = new HashMap<>();
      boolean success = false;
      String description = "";
      String errorMessage = "";
      if (!this.checkNonvmmServiceHealth()) {
         errorMessage = "描述服务不可用，请稍后再试";
         log.error(errorMessage);
         result.put("success", false);
         result.put("message", errorMessage);
         this.saveGenerationLog(currentUser, "IMAGE_TO_TEXT", request.getImageUrl(), null, null, "small", false, false, errorMessage);
         return result;
      }

      try {
         String serviceUrl = this.serviceConfig.getNonvmmUrl() + "/describe_image";
         log.info("发送图像描述请求到: {}", serviceUrl);
         int maxRetries = 2;
         int currentRetry = 0;
         boolean requestSuccessful = false;
         ResponseEntity<Map> response = null;

         while (currentRetry <= maxRetries && !requestSuccessful) {
            try {
               response = this.restTemplate.postForEntity(serviceUrl, requestData, Map.class, new Object[0]);
               requestSuccessful = true;
            } catch (RestClientException ex) {
               if (++currentRetry > maxRetries) {
                  throw ex;
               }

               log.warn("请求图像描述失败，尝试重试 ({}/{}): {}", new Object[]{currentRetry, maxRetries, ex.getMessage()});
               TimeUnit.SECONDS.sleep(1L);
            }
         }

         if (requestSuccessful && response != null && response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseBody = (Map<String, Object>)response.getBody();
            description = (String)responseBody.get("description");
            success = true;
            result.put("success", true);
            result.put("description", description);
            result.put("message", "图像描述生成成功");
         } else {
            String statusCode = response != null ? response.getStatusCode().toString() : "未知";
            errorMessage = "描述服务返回错误: " + statusCode;
            result.put("success", false);
            result.put("message", errorMessage);
         }
      } catch (ResourceAccessException e) {
         if (e.getCause() instanceof SocketTimeoutException) {
            errorMessage = "描述服务响应超时，请稍后再试";
         } else {
            errorMessage = "无法连接到描述服务";
         }

         log.error("描述服务连接异常: type={}", e.getClass().getSimpleName());
         result.put("success", false);
         result.put("message", errorMessage);
      } catch (Exception e) {
         log.error("生成图像描述时发生错误: type={}", e.getClass().getSimpleName());
         errorMessage = "生成图像描述时发生错误";
         result.put("success", false);
         result.put("message", errorMessage);
      }

      this.saveGenerationLog(currentUser, "IMAGE_TO_TEXT", request.getImageUrl(), null, description, "small", false, success, errorMessage);
      return result;
   }

   public Map<String, Object> generateMusic(GenerateMusicRequest request) {
      log.info("生成音乐: modelSize={}, useFastGenerate={}, duration={}", new Object[]{request.getModelSize(), request.getUseFastGenerate(), request.getDuration()});
      User currentUser = this.getCurrentUser();
      Map<String, Object> requestData = new HashMap<>();
      requestData.put("image", this.convertImageToBase64(request.getImageUrl()));
      requestData.put("duration", request.getDuration());
      boolean useFastGenerate = request.getUseFastGenerate() != null ? request.getUseFastGenerate() : false;
      Map<String, Object> result = new HashMap<>();
      boolean success = false;
      String resultUrl = "";
      String description = "";
      String errorMessage = "";

      try {
         String serviceUrl;
         if (useFastGenerate) {
            boolean serviceHealthy = this.checkVmmServiceHealth();
            if (!serviceHealthy) {
               errorMessage = "快速生成服务不可用，请尝试使用标准模式";
               log.error(errorMessage);
               result.put("success", false);
               result.put("message", errorMessage);
               this.saveGenerationLog(
                  currentUser,
                  "IMAGE_TO_MUSIC",
                  request.getImageUrl(),
                  null,
                  null,
                  request.getModelSize(),
                  useFastGenerate,
                  request.getDuration(),
                  false,
                  errorMessage
               );
               return result;
            }

            if (request.getTextDescription() != null && !request.getTextDescription().trim().isEmpty()) {
               serviceUrl = this.serviceConfig.getVmmUrl() + "/api/generate_with_image_and_text";
               requestData.put("text_description", request.getTextDescription());
               log.info("使用VMM模式(图像+文本)生成音乐，转发请求到: {}", serviceUrl);
            } else {
               serviceUrl = this.serviceConfig.getVmmUrl() + "/api/generate_with_image";
               log.info("使用VMM模式(仅图像)生成音乐，转发请求到: {}", serviceUrl);
            }
         } else {
            boolean serviceHealthy = this.checkNonvmmServiceHealth();
            if (!serviceHealthy) {
               errorMessage = "标准生成服务不可用，请尝试使用快速模式";
               log.error(errorMessage);
               result.put("success", false);
               result.put("message", errorMessage);
               this.saveGenerationLog(
                  currentUser,
                  "IMAGE_TO_MUSIC",
                  request.getImageUrl(),
                  null,
                  null,
                  request.getModelSize(),
                  useFastGenerate,
                  request.getDuration(),
                  false,
                  errorMessage
               );
               return result;
            }

            serviceUrl = this.serviceConfig.getNonvmmUrl() + "/generate";
            requestData.put("modelSize", request.getModelSize());
            log.info("使用非VMM模式生成音乐，模型: {}, 转发请求到: {}", request.getModelSize(), serviceUrl);
         }

         int maxRetries = 1;
         int currentRetry = 0;
         boolean requestSuccessful = false;
         ResponseEntity<Map> response = null;

         while (currentRetry <= maxRetries && !requestSuccessful) {
            try {
               response = this.restTemplate.postForEntity(serviceUrl, requestData, Map.class, new Object[0]);
               requestSuccessful = true;
            } catch (RestClientException ex) {
               if (++currentRetry > maxRetries) {
                  throw ex;
               }

               log.warn("生成音乐请求失败，尝试重试 ({}/{}): {}", new Object[]{currentRetry, maxRetries, ex.getMessage()});
               TimeUnit.SECONDS.sleep(2L);
            }
         }

         if (response != null && response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseBody = (Map<String, Object>)response.getBody();
            success = true;
            if (responseBody.containsKey("fileName")) {
               resultUrl = (String)responseBody.get("fileName");
            } else if (responseBody.containsKey("audio_url")) {
               resultUrl = ((String)responseBody.get("audio_url")).replace("/audios/", "");
            }

            if (responseBody.containsKey("text_description")) {
               description = (String)responseBody.get("text_description");
               result.put("description", description);
            }

            result.put("success", true);
            result.put("fileName", resultUrl);
            result.put("message", "音乐生成成功");
         } else {
            String statusCode = response != null ? response.getStatusCode().toString() : "未知";
            errorMessage = "模型服务返回错误: " + statusCode;
            result.put("success", false);
            result.put("message", errorMessage);
         }
      } catch (ResourceAccessException e) {
         if (e.getCause() instanceof SocketTimeoutException) {
            errorMessage = "音乐生成服务响应超时，请稍后再试";
         } else {
            errorMessage = "无法连接到音乐生成服务";
         }

         log.error("音乐生成服务连接异常: type={}", e.getClass().getSimpleName());
         result.put("success", false);
         result.put("message", errorMessage);
      } catch (Exception e) {
         log.error("生成音乐时发生错误: type={}", e.getClass().getSimpleName());
         errorMessage = "生成音乐时发生错误";
         result.put("success", false);
         result.put("message", errorMessage);
      }

      this.saveGenerationLog(
         currentUser,
         "IMAGE_TO_MUSIC",
         request.getImageUrl(),
         resultUrl,
         description,
         request.getModelSize(),
         useFastGenerate,
         request.getDuration(),
         success,
         errorMessage
      );
      return result;
   }

   private boolean checkVmmServiceHealth() {
      long currentTime = System.currentTimeMillis();
      if (currentTime - this.lastVmmHealthCheck < 60000L) {
         return this.vmmServiceHealthy;
      }

      try {
         ResponseEntity<Object> response = this.restTemplate
            .exchange(this.serviceConfig.getVmmUrl() + "/health", HttpMethod.GET, null, Object.class, new Object[0]);
         this.vmmServiceHealthy = response.getStatusCode().is2xxSuccessful();
      } catch (Exception e) {
         log.warn("VMM服务健康检查失败: {}", e.getMessage());
         this.vmmServiceHealthy = false;
      }

      this.lastVmmHealthCheck = currentTime;
      return this.vmmServiceHealthy;
   }

   private boolean checkNonvmmServiceHealth() {
      long currentTime = System.currentTimeMillis();
      if (currentTime - this.lastNonvmmHealthCheck < 60000L) {
         return this.nonvmmServiceHealthy;
      }

      try {
         ResponseEntity<Object> response = this.restTemplate
            .exchange(this.serviceConfig.getNonvmmUrl() + "/health", HttpMethod.GET, null, Object.class, new Object[0]);
         this.nonvmmServiceHealthy = response.getStatusCode().is2xxSuccessful();
      } catch (Exception e) {
         log.warn("非VMM服务健康检查失败: {}", e.getMessage());
         this.nonvmmServiceHealthy = false;
      }

      this.lastNonvmmHealthCheck = currentTime;
      return this.nonvmmServiceHealthy;
   }

   public Map<String, Object> recordApiUsage(RecordApiUsageRequest request) {
      User currentUser = this.getCurrentUser();
      GenerationLog log = GenerationLog.builder()
         .user(currentUser)
         .taskType(request.getTaskType())
         .apiSource(request.getApiSource())
         .apiProvider(request.getApiProvider())
         .inputData(request.getInputData())
         .outputData(request.getOutputData())
         .imageUrl(request.getImageUrl())
         .resultUrl(request.getResultUrl())
         .description(request.getDescription())
         .modelSize(request.getModelSize() != null ? request.getModelSize() : "unknown")
         .useFastGenerate(request.getUseFastGenerate() != null ? request.getUseFastGenerate() : false)
         .duration(request.getDuration())
         .processingTimeMs(request.getProcessingTimeMs())
         .success(request.getSuccess())
         .errorMessage(request.getErrorMessage())
         .metadata(request.getMetadata())
         .build();
      GenerationLog savedLog = (GenerationLog)this.generationLogRepository.save(log);
      Map<String, Object> result = new HashMap<>();
      result.put("logId", savedLog.getId());
      result.put("success", true);
      result.put("message", "API使用记录保存成功");
      return result;
   }

   private void saveGenerationLog(
      User user,
      String type,
      String imageUrl,
      String resultUrl,
      String description,
      String modelSize,
      boolean useFastGenerate,
      boolean success,
      String errorMessage
   ) {
      GenerationLog log = GenerationLog.builder()
         .user(user)
         .taskType(type)
         .apiSource("画音智链-墨韵弦思")
         .apiProvider("画音智链墨韵弦思模型")
         .imageUrl(imageUrl)
         .resultUrl(resultUrl)
         .description(description)
         .modelSize(modelSize)
         .useFastGenerate(useFastGenerate)
         .success(success)
         .errorMessage(errorMessage)
         .build();
      this.generationLogRepository.save(log);
   }

   private void saveGenerationLog(
      User user,
      String type,
      String imageUrl,
      String resultUrl,
      String description,
      String modelSize,
      boolean useFastGenerate,
      Integer duration,
      boolean success,
      String errorMessage
   ) {
      String apiSource = useFastGenerate ? "画音智链-墨韵音声-快速生成" : "画音智链-墨韵音声-标准生成";
      String apiProvider = useFastGenerate ? "画音智链墨韵音声模型" : "画音智链墨韵音声模型";
      GenerationLog log = GenerationLog.builder()
         .user(user)
         .taskType(type)
         .apiSource(apiSource)
         .apiProvider(apiProvider)
         .imageUrl(imageUrl)
         .resultUrl(resultUrl)
         .description(description)
         .modelSize(modelSize)
         .useFastGenerate(useFastGenerate)
         .duration(duration)
         .success(success)
         .errorMessage(errorMessage)
         .build();
      this.generationLogRepository.save(log);
   }

   public Map<String, Object> uploadResult(UploadResultRequest request) {
      User currentUser = this.getCurrentUser();
      Map<String, Object> result = new HashMap<>();

      try {
         GenerationLog generationLog = (GenerationLog)this.generationLogRepository
            .findById(request.getLogId())
            .orElseThrow(() -> new IllegalArgumentException("找不到ID为 " + request.getLogId() + " 的日志记录"));
         if (!generationLog.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("无权限访问该日志记录");
         }

         String relativePath = null;
         if (request.getBase64Data() != null && !request.getBase64Data().trim().isEmpty()) {
            relativePath = this.storageService
               .storeBase64File(request.getBase64Data(), request.getContentType(), request.getFileExtension(), currentUser.getId());
         } else if (request.getRemoteUrl() != null && !request.getRemoteUrl().trim().isEmpty()) {
            relativePath = this.storageService
               .storeRemoteFile(request.getRemoteUrl(), request.getContentType(), request.getFileExtension(), currentUser.getId());
         }

         if (relativePath != null) {
            generationLog.setResultUrl(relativePath);
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
               generationLog.setDescription(request.getDescription());
            }

            this.generationLogRepository.save(generationLog);
            result.put("success", true);
            result.put("relativePath", relativePath);
            result.put("absolutePath", relativePath);
            result.put("logId", request.getLogId());
            result.put("contentType", request.getContentType());
            log.info("结果文件已保存并关联到日志记录 {}: {}", request.getLogId(), relativePath);
         } else {
            result.put("success", false);
            result.put("message", "文件保存失败");
         }
      } catch (Exception e) {
         log.error("上传结果失败: type={}", e.getClass().getSimpleName());
         result.put("success", false);
         result.put("message", "结果文件保存失败");
      }

      return result;
   }

   private User getCurrentUser() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return (User)authentication.getPrincipal();
   }

   private String convertImageToBase64(String imageUrl) {
      try {
         if (imageUrl != null && !imageUrl.isBlank()) {
            String normalizedReference = imageUrl.replace('\\', '/');
            int queryIndex = normalizedReference.indexOf(63);
            if (queryIndex >= 0) {
               normalizedReference = normalizedReference.substring(0, queryIndex);
            }

            int fragmentIndex = normalizedReference.indexOf(35);
            if (fragmentIndex >= 0) {
               normalizedReference = normalizedReference.substring(0, fragmentIndex);
            }

            String filename = normalizedReference.substring(normalizedReference.lastIndexOf(47) + 1);
            Path storedImage = this.storageService.resolveStoredFile(filename);
            if (!Files.isRegularFile(storedImage)) {
               throw new IllegalArgumentException("图片不存在");
            }

            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(storedImage));
            String lowerFilename = filename.toLowerCase(Locale.ROOT);
            String mime = !lowerFilename.endsWith(".jpg") && !lowerFilename.endsWith(".jpeg") ? "image/png" : "image/jpeg";
            return "data:" + mime + ";base64," + base64;
         } else {
            throw new IllegalArgumentException("图片引用不能为空");
         }
      } catch (Exception exception) {
         throw new IllegalArgumentException("图片转换base64失败", exception);
      }
   }

   public GenerationService(
      final RestTemplate restTemplate,
      final ServiceProperties serviceConfig,
      final GenerationLogRepository generationLogRepository,
      final StorageService storageService
   ) {
      this.restTemplate = restTemplate;
      this.serviceConfig = serviceConfig;
      this.generationLogRepository = generationLogRepository;
      this.storageService = storageService;
   }
}

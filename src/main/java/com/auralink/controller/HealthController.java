package com.auralink.controller;

import com.auralink.config.properties.ServiceProperties;
import com.auralink.config.properties.StorageProperties;
import com.auralink.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping({"/", "/api"})
public class HealthController {
   private static final Logger log = LoggerFactory.getLogger(HealthController.class);
   private final UserRepository userRepository;
   private final RestTemplate restTemplate;
   private final ServiceProperties serviceProperties;
   private final StorageProperties storageProperties;

   @GetMapping("/health")
   public ResponseEntity<Map<String, Object>> healthCheck() {
      Map<String, Object> status = new HashMap<>();
      status.put("status", "UP");
      status.put("message", "Auralink后端服务运行中");
      return ResponseEntity.ok(status);
   }

   @GetMapping("/health/details")
   public ResponseEntity<Map<String, Object>> detailedHealthCheck() {
      Map<String, Object> status = new HashMap<>();
      Map<String, Object> components = new HashMap<>();
      boolean isSystemHealthy = true;

      try {
         long userCount = this.userRepository.count();
         components.put("database", Map.of("status", "UP", "details", "数据库连接正常，用户数: " + userCount));
      } catch (Exception exception) {
         isSystemHealthy = false;
         components.put("database", Map.of("status", "DOWN", "error", "database unavailable"));
         log.error("Database health check failed");
      }

      components.put("vmm-service", this.checkService(this.serviceProperties.getVmmUrl(), "VMM"));
      components.put("nonvmm-service", this.checkService(this.serviceProperties.getNonvmmUrl(), "Qwen"));

      try {
         Path uploadDir = Path.of(this.storageProperties.getUploadDir()).toAbsolutePath().normalize();
         boolean exists = Files.isDirectory(uploadDir);
         boolean writable = exists && Files.isWritable(uploadDir);
         components.put(
            "file-system",
            Map.of(
               "status",
               writable ? "UP" : "WARNING",
               "configured",
               StringUtils.hasText(this.storageProperties.getUploadDir()),
               "exists",
               exists,
               "writable",
               writable
            )
         );
      } catch (Exception exception) {
         isSystemHealthy = false;
         components.put("file-system", Map.of("status", "WARNING", "error", "storage unavailable"));
         log.error("Storage health check failed");
      }

      status.put("status", isSystemHealthy ? "UP" : "DOWN");
      status.put("components", components);
      return ResponseEntity.ok(status);
   }

   private Map<String, Object> checkService(String serviceUrl, String serviceName) {
      boolean configured = StringUtils.hasText(serviceUrl);
      if (!configured) {
         return Map.of("status", "DOWN", "configured", false, "reachable", false);
      }

      try {
         String healthUrl = serviceUrl.endsWith("/") ? serviceUrl + "health" : serviceUrl + "/health";
         this.restTemplate.getForEntity(healthUrl, Object.class, new Object[0]);
         return Map.of("status", "UP", "configured", true, "reachable", true);
      } catch (RuntimeException exception) {
         log.warn("{} service health check failed", serviceName);
         return Map.of("status", "DOWN", "configured", true, "reachable", false);
      }
   }

   public HealthController(
      final UserRepository userRepository,
      final RestTemplate restTemplate,
      final ServiceProperties serviceProperties,
      final StorageProperties storageProperties
   ) {
      this.userRepository = userRepository;
      this.restTemplate = restTemplate;
      this.serviceProperties = serviceProperties;
      this.storageProperties = storageProperties;
   }
}

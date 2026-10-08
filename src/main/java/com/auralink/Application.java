package com.auralink;

import com.auralink.config.properties.StorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class Application {
   private static final Logger log = LoggerFactory.getLogger(Application.class);
   private final StorageProperties storageProperties;

   @PostConstruct
   void init() {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
      this.createDirectoryIfPossible(this.storageProperties.getUploadDir(), "upload");
      this.createDirectoryIfPossible(this.storageProperties.getAudioDir(), "audio");
      this.createDirectoryIfPossible(this.storageProperties.getLegacyFrontendAudioDir(), "legacy frontend audio");
   }

   private void createDirectoryIfPossible(String configuredPath, String directoryType) {
      try {
         Files.createDirectories(Path.of(configuredPath));
      } catch (IOException | RuntimeException exception) {
         log.warn("Unable to initialize configured {} directory", directoryType);
      }
   }

   public static void main(String[] args) {
      SpringApplication.run(Application.class, args);
   }

   public Application(final StorageProperties storageProperties) {
      this.storageProperties = storageProperties;
   }
}

package com.auralink.controller;

import com.auralink.dto.GenerateMusicRequest;
import com.auralink.dto.ImageDescriptionRequest;
import com.auralink.service.GenerationService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyApiController {
   private static final Logger log = LoggerFactory.getLogger(LegacyApiController.class);
   private final GenerationService generationService;

   @GetMapping("/models")
   public ResponseEntity<Map<String, Object>> getModels() {
      Map<String, Object> models = this.generationService.getModels();
      return ResponseEntity.ok(models);
   }

   @PostMapping("/describe_image")
   public ResponseEntity<Map<String, Object>> describeImage(@RequestBody Map<String, Object> request) {
      log.info("旧版API - 描述图像: {}", request.get("image"));
      ImageDescriptionRequest req = new ImageDescriptionRequest();
      req.setImageUrl((String)request.get("image"));
      Map<String, Object> result = this.generationService.generateImageDescription(req);
      return ResponseEntity.ok(result);
   }

   @PostMapping("/generate")
   public ResponseEntity<Map<String, Object>> generateMusic(@RequestBody Map<String, Object> request) {
      log.info("旧版API - 生成音乐: {}", request);
      String imageUrl = (String)request.get("image");
      String modelSize = (String)request.getOrDefault("modelSize", "small");
      Integer duration = (Integer)request.getOrDefault("duration", 30);
      String mode = (String)request.getOrDefault("mode", "nonvmm");
      String textDescription = (String)request.get("text_description");
      GenerateMusicRequest req = GenerateMusicRequest.builder()
         .imageUrl(imageUrl)
         .modelSize(modelSize)
         .useFastGenerate("vmm".equalsIgnoreCase(mode))
         .duration(duration)
         .textDescription(textDescription)
         .build();
      Map<String, Object> result = this.generationService.generateMusic(req);
      return ResponseEntity.ok(result);
   }

   public LegacyApiController(final GenerationService generationService) {
      this.generationService = generationService;
   }
}

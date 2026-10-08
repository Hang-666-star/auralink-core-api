package com.auralink.controller;

import com.auralink.config.properties.PaintingProperties;
import com.auralink.dto.ApiResponse;
import com.auralink.service.PaintingCatalogService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/paintings")
public class PaintingController {
   private static final Logger log = LoggerFactory.getLogger(PaintingController.class);
   private final PaintingCatalogService paintingCatalogService;
   private final PaintingProperties paintingConfig;

   @GetMapping
   public ResponseEntity<ApiResponse<Map<String, Object>>> listPaintings(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String dynasty,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset
   ) {
      PaintingCatalogService.PaintingPage page = this.paintingCatalogService.search(query, dynasty, limit, offset);
      List<Map<String, Object>> items = page.items().stream().map(this::withImageUrl).toList();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("total", page.total());
      result.put("limit", page.limit());
      result.put("offset", page.offset());
      result.put("items", items);
      return ResponseEntity.ok(ApiResponse.success("Paintings loaded", result));
   }

   @GetMapping("/images/{fileName:.+}")
   public ResponseEntity<Resource> servePaintingImage(@PathVariable String fileName) {
      try {
         Optional<Path> resolvedPath = this.paintingCatalogService.resolveImagePath(fileName);
         if (resolvedPath.isEmpty()) {
            return ResponseEntity.notFound().build();
         }

         Path imagePath = resolvedPath.get();
         Resource resource = new UrlResource(imagePath.toUri());
         if (resource.exists() && resource.isReadable()) {
            String contentType = Files.probeContentType(imagePath);
            if (contentType == null || contentType.isBlank()) {
               contentType = "image/jpeg";
            }

            return ((BodyBuilder)((BodyBuilder)ResponseEntity.ok().cacheControl(CacheControl.noCache()))
                  .header("Content-Disposition", new String[]{"inline; filename=\"" + imagePath.getFileName() + "\""}))
               .contentType(MediaType.parseMediaType(contentType))
               .body(resource);
         } else {
            return ResponseEntity.notFound().build();
         }
      } catch (IOException ex) {
         log.error("Failed to serve painting image: {}", fileName, ex);
         return ResponseEntity.internalServerError().build();
      }
   }

   private Map<String, Object> withImageUrl(Map<String, Object> item) {
      Map<String, Object> result = new LinkedHashMap<>(item);
      String imageFileName = this.extractImageFileName(item);
      if (!StringUtils.hasText(imageFileName)) {
         result.put("imageUrl", "");
         return result;
      } else if (StringUtils.hasText(this.paintingConfig.getImageBaseUrl())) {
         String baseUrl = this.paintingConfig.getImageBaseUrl().trim();
         String encodedName = URLEncoder.encode(imageFileName, StandardCharsets.UTF_8).replace("+", "%20");
         String imageUrl = baseUrl.endsWith("/") ? baseUrl + encodedName : baseUrl + "/" + encodedName;
         result.put("imageUrl", imageUrl);
         return result;
      } else {
         String imageUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/paintings/images/")
            .pathSegment(new String[]{imageFileName})
            .toUriString();
         result.put("imageUrl", imageUrl);
         return result;
      }
   }

   private String extractImageFileName(Map<String, Object> item) {
      if (item.get("imageFileName") instanceof String value && StringUtils.hasText(value)) {
         return value.trim();
      } else if (item.get("imageStorageName") instanceof String value && StringUtils.hasText(value)) {
         String normalized = value.trim().replace('（', '(').replace('）', ')').replaceAll("\\s+", " ");
         return !normalized.toLowerCase().endsWith(".jpg") && !normalized.toLowerCase().endsWith(".jpeg") ? normalized + ".jpg" : normalized;
      } else {
         return "";
      }
   }

   public PaintingController(final PaintingCatalogService paintingCatalogService, final PaintingProperties paintingConfig) {
      this.paintingCatalogService = paintingCatalogService;
      this.paintingConfig = paintingConfig;
   }
}

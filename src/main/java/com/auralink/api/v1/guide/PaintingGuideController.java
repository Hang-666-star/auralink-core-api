package com.auralink.api.v1.guide;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.catalogguide.PostgresPaintingGuideService;
import com.auralink.guide.service.PaintingGuideService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/paintings/{paintingId}/guide")
public class PaintingGuideController {
   private final PaintingGuideService guideService;
   private final ObjectProvider<PostgresPaintingGuideService> postgresGuideServiceProvider;

   @GetMapping
   public PaintingGuideResponse getGuide(@PathVariable String paintingId) {
      PostgresPaintingGuideService postgres = (PostgresPaintingGuideService)this.postgresGuideServiceProvider.getIfAvailable();
      return PaintingGuideResponse.from(postgres == null ? this.guideService.getCurrentGuide(paintingId) : postgres.getCurrentGuide(paintingId));
   }

   @GetMapping("/history")
   public HistoricalPaintingGuideResponse getHistoricalGuide(@PathVariable String paintingId) {
      PostgresPaintingGuideService postgres = (PostgresPaintingGuideService)this.postgresGuideServiceProvider.getIfAvailable();
      if (postgres == null) {
         throw new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.GUIDE_HISTORICAL_NOT_AVAILABLE, "该画作没有可安全呈现的历史导览");
      } else {
         return HistoricalPaintingGuideResponse.from(postgres.getHistoricalGuide(paintingId));
      }
   }

   @PostMapping
   public PaintingGuideResponse ensureGuide(@PathVariable String paintingId, Authentication authentication) {
      String requester = authentication == null ? null : authentication.getName();
      PostgresPaintingGuideService postgres = (PostgresPaintingGuideService)this.postgresGuideServiceProvider.getIfAvailable();
      return PaintingGuideResponse.from(postgres == null ? this.guideService.ensureGuide(paintingId, requester) : postgres.ensureGuide(paintingId, requester));
   }

   @PostMapping("/audio")
   public ResponseEntity<Void> reservedAudio(@PathVariable String paintingId) {
      PostgresPaintingGuideService postgres = (PostgresPaintingGuideService)this.postgresGuideServiceProvider.getIfAvailable();
      if (postgres == null) {
         this.guideService.requirePaintingForReservedAudio(paintingId);
      } else {
         postgres.requirePaintingForReservedAudio(paintingId);
      }

      return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
   }

   public PaintingGuideController(final PaintingGuideService guideService, final ObjectProvider<PostgresPaintingGuideService> postgresGuideServiceProvider) {
      this.guideService = guideService;
      this.postgresGuideServiceProvider = postgresGuideServiceProvider;
   }
}

package com.auralink.api.v1.painting;

import com.auralink.service.painting.PaintingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/favorites/paintings")
public class MyPaintingFavoriteController {
   private final PaintingQueryService paintingQueryService;

   @GetMapping
   public PaintingPageResponse listFavorites(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
      return this.paintingQueryService.listCurrentUserFavorites(page, size);
   }

   public MyPaintingFavoriteController(final PaintingQueryService paintingQueryService) {
      this.paintingQueryService = paintingQueryService;
   }
}

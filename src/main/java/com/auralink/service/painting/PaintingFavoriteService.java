package com.auralink.service.painting;

import com.auralink.catalogauth.CatalogAuthStore;
import com.auralink.entity.Painting;
import com.auralink.entity.User;
import com.auralink.repository.PaintingFavoriteRepository;
import com.auralink.service.CurrentUserService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaintingFavoriteService {
   private final PaintingFavoriteRepository favoriteRepository;
   private final PaintingQueryService paintingQueryService;
   private final CurrentUserService currentUserService;
   private final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider;

   @Transactional
   public void favorite(String paintingId) {
      User user = this.currentUserService.requireCurrentUser();
      CatalogAuthStore catalogAuthStore = (CatalogAuthStore)this.catalogAuthStoreProvider.getIfAvailable();
      if (catalogAuthStore != null) {
         catalogAuthStore.favorite(user.getId(), paintingId);
      } else {
         Painting painting = this.paintingQueryService.requireActivePainting(paintingId);
         this.favoriteRepository.insertIfAbsent(UUID.randomUUID().toString(), user.getId(), painting.getId(), LocalDateTime.now());
      }
   }

   @Transactional
   public void unfavorite(String paintingId) {
      User user = this.currentUserService.requireCurrentUser();
      CatalogAuthStore catalogAuthStore = (CatalogAuthStore)this.catalogAuthStoreProvider.getIfAvailable();
      if (catalogAuthStore != null) {
         catalogAuthStore.unfavorite(user.getId(), paintingId);
      } else {
         Painting painting = this.paintingQueryService.requireActivePainting(paintingId);
         this.favoriteRepository.deleteByUserIdAndPaintingId(user.getId(), painting.getId());
      }
   }

   public PaintingFavoriteService(
      final PaintingFavoriteRepository favoriteRepository,
      final PaintingQueryService paintingQueryService,
      final CurrentUserService currentUserService,
      final ObjectProvider<CatalogAuthStore> catalogAuthStoreProvider
   ) {
      this.favoriteRepository = favoriteRepository;
      this.paintingQueryService = paintingQueryService;
      this.currentUserService = currentUserService;
      this.catalogAuthStoreProvider = catalogAuthStoreProvider;
   }
}

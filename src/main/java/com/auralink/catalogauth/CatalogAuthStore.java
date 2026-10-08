package com.auralink.catalogauth;

import com.auralink.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CatalogAuthStore {
   Optional<User> findByUsername(String username);

   boolean existsByUsername(String username);

   boolean existsByEmail(String email);

   User register(String username, String encodedPassword, String fullName, String email);

   Set<String> favoritedPaintingIds(long userId, Collection<String> paintingIds);

   boolean isFavorited(long userId, String paintingId);

   void favorite(long userId, String paintingId);

   void unfavorite(long userId, String paintingId);

   CatalogAuthStore.FavoritePage favoritePaintingIds(long userId, int page, int size);

   record FavoritePage(List<String> paintingIds, int page, int size, long totalElements) {
      public FavoritePage {
         paintingIds = List.copyOf(paintingIds);
      }
   }
}

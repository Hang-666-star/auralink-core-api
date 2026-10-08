package com.auralink.repository;

import com.auralink.entity.PaintingFavorite;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaintingFavoriteRepository extends JpaRepository<PaintingFavorite, Long> {
   Optional<PaintingFavorite> findByPublicId(String publicId);

   boolean existsByPublicId(String publicId);

   boolean existsByUserIdAndPaintingId(Long userId, Long paintingId);

   Optional<PaintingFavorite> findByUserIdAndPaintingId(Long userId, Long paintingId);

   @Modifying(flushAutomatically = true, clearAutomatically = true)
   @Query(
      value = "INSERT OR IGNORE INTO painting_favorites\n    (public_id, user_id, painting_id, created_at)\nVALUES\n    (:publicId, :userId, :paintingId, :createdAt)\n",
      nativeQuery = true
   )
   int insertIfAbsent(
      @Param("publicId") String publicId, @Param("userId") Long userId, @Param("paintingId") Long paintingId, @Param("createdAt") LocalDateTime createdAt
   );

   long deleteByUserIdAndPaintingId(Long userId, Long paintingId);

   @EntityGraph(attributePaths = {"painting", "painting.imageAsset"})
   Page<PaintingFavorite> findByUserIdAndPaintingStatus(Long userId, String paintingStatus, Pageable pageable);

   @Query("SELECT favorite.painting.publicId\nFROM PaintingFavorite favorite\nWHERE favorite.user.id = :userId\n  AND favorite.painting.id IN :paintingIds\n")
   List<String> findFavoritedPaintingPublicIds(@Param("userId") Long userId, @Param("paintingIds") Collection<Long> paintingIds);
}

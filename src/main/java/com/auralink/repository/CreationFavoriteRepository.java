package com.auralink.repository;

import com.auralink.entity.CreationFavorite;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreationFavoriteRepository extends JpaRepository<CreationFavorite, Long> {
   Optional<CreationFavorite> findByPublicId(String publicId);

   boolean existsByPublicId(String publicId);

   boolean existsByUserIdAndCreationId(Long userId, Long creationId);
}

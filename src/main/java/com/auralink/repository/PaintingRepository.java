package com.auralink.repository;

import com.auralink.entity.Painting;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PaintingRepository extends JpaRepository<Painting, Long>, JpaSpecificationExecutor<Painting> {
   Optional<Painting> findByPublicId(String publicId);

   @EntityGraph(attributePaths = "imageAsset")
   Optional<Painting> findByPublicIdAndStatus(String publicId, String status);

   boolean existsByPublicId(String publicId);

   Optional<Painting> findBySourceKey(String sourceKey);

   boolean existsBySourceKey(String sourceKey);

   @EntityGraph(attributePaths = "imageAsset")
   Page<Painting> findAll(Specification<Painting> specification, Pageable pageable);
}

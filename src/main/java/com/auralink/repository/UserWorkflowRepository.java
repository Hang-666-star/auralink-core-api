package com.auralink.repository;

import com.auralink.entity.UserWorkflow;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserWorkflowRepository extends JpaRepository<UserWorkflow, Long> {
   Optional<UserWorkflow> findByPublicId(String publicId);

   boolean existsByPublicId(String publicId);

   Optional<UserWorkflow> findByPublicIdAndUserId(String publicId, Long userId);

   Optional<UserWorkflow> findByPublicIdAndUser_IdAndStatus(String publicId, Long userId, String status);

   Optional<UserWorkflow> findByPublicIdAndUser_Id(String publicId, Long userId);

   Page<UserWorkflow> findAllByUser_Id(Long userId, Pageable pageable);

   Page<UserWorkflow> findAllByUser_IdAndStatus(Long userId, String status, Pageable pageable);

   long deleteByPublicIdAndUser_Id(String publicId, Long userId);
}

package com.auralink.guide.service;

import com.auralink.entity.Painting;
import com.auralink.entity.PaintingGuide;
import com.auralink.repository.PaintingGuideRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaintingGuideCacheStore {
   public static final String SUCCESS_STATUS = "SUCCESS";
   private final PaintingGuideRepository repository;

   @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
   public Optional<PaintingGuide> findByPaintingId(Long paintingId) {
      return this.repository.findByPaintingId(paintingId);
   }

   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public PaintingGuide saveSuccess(Painting painting, String sourceHash, String canonicalResultJson) {
      PaintingGuide guide = this.repository.findByPaintingId(painting.getId()).orElseGet(() -> PaintingGuide.builder().painting(painting).build());
      LocalDateTime now = LocalDateTime.now();
      guide.setSourceHash(sourceHash);
      guide.setResultJson(canonicalResultJson);
      guide.setStatus("SUCCESS");
      guide.setGeneratedAt(now);
      guide.setUpdatedAt(now);
      return (PaintingGuide)this.repository.saveAndFlush(guide);
   }

   public PaintingGuideCacheStore(final PaintingGuideRepository repository) {
      this.repository = repository;
   }
}

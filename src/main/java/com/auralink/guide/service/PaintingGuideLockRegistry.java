package com.auralink.guide.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PaintingGuideLockRegistry {
   static final int MAX_PARTICIPANTS_PER_PAINTING = 4;
   private final ConcurrentHashMap<String, PaintingGuideLockRegistry.LockEntry> locks = new ConcurrentHashMap<>();

   public <T> T withPaintingLock(String paintingId, Supplier<T> action) {
      PaintingGuideLockRegistry.LockEntry entry = this.locks.compute(paintingId, (ignored, existing) -> {
         PaintingGuideLockRegistry.LockEntry selected = existing == null ? new PaintingGuideLockRegistry.LockEntry() : existing;
         if (selected.references >= 4) {
            throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_PROVIDER_UNAVAILABLE, "画作导览生成服务当前繁忙");
         }

         selected.references++;
         return selected;
      });
      entry.lock.lock();

      try {
         return action.get();
      } finally {
         entry.lock.unlock();
         this.locks.computeIfPresent(paintingId, (ignored, current) -> {
            current.references--;
            return (PaintingGuideLockRegistry.LockEntry)(current.references == 0 ? null : current);
         });
      }
   }

   int activeEntryCount() {
      return this.locks.size();
   }

   int activeParticipantCount(String paintingId) {
      PaintingGuideLockRegistry.LockEntry entry = this.locks.get(paintingId);
      return entry == null ? 0 : entry.references;
   }

   private static final class LockEntry {
      private final ReentrantLock lock = new ReentrantLock();
      private volatile int references;
   }
}

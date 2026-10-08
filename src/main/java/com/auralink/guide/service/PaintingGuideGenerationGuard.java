package com.auralink.guide.service;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.config.properties.GuideProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PaintingGuideGenerationGuard {
   private static final int MAX_REQUESTER_LENGTH = 256;
   private final GuideProperties properties;
   private final Clock clock;
   private final Semaphore providerPermits;
   private final Map<String, ArrayDeque<Long>> usage = new HashMap<>();
   private final ArrayDeque<Long> globalUsage = new ArrayDeque<>();

   @Autowired
   public PaintingGuideGenerationGuard(GuideProperties properties) {
      this(properties, Clock.systemUTC());
   }

   PaintingGuideGenerationGuard(GuideProperties properties, Clock clock) {
      this.properties = properties;
      this.clock = clock;
      this.providerPermits = new Semaphore(properties.getMaxConcurrentGenerations(), true);
   }

   public synchronized void recordCacheMiss(String requester) {
      String key = this.requesterKey(requester);
      long now = this.clock.millis();
      long userWindowMillis = this.positiveWindow().toMillis();
      long globalWindowMillis = this.positiveGlobalWindow().toMillis();
      this.prune(this.globalUsage, now, globalWindowMillis);
      if (this.globalUsage.size() >= this.properties.getGlobalGenerationLimit()) {
         throw new ApiV1Exception(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.GUIDE_RATE_LIMITED, "画作导览生成额度暂时已用完");
      }

      ArrayDeque<Long> currentUser = this.usage.get(key);
      if (currentUser != null) {
         this.prune(currentUser, now, userWindowMillis);
      }

      if (currentUser != null && currentUser.size() >= this.properties.getUserGenerationLimit()) {
         throw new ApiV1Exception(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.GUIDE_RATE_LIMITED, "画作导览生成请求过于频繁");
      }

      if (currentUser == null) {
         currentUser = new ArrayDeque<>();
         this.usage.put(key, currentUser);
      }

      currentUser.addLast(now);
      this.globalUsage.addLast(now);
      if (this.usage.size() > 1024) {
         Iterator<Entry<String, ArrayDeque<Long>>> iterator = this.usage.entrySet().iterator();

         while (iterator.hasNext()) {
            ArrayDeque<Long> timestamps = iterator.next().getValue();
            this.prune(timestamps, now, userWindowMillis);
            if (timestamps.isEmpty()) {
               iterator.remove();
            }
         }
      }
   }

   public <T> T withPaidGeneration(String requester, Supplier<T> action) {
      if (!this.providerPermits.tryAcquire()) {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_PROVIDER_UNAVAILABLE, "画作导览生成服务当前繁忙");
      }

      try {
         this.recordCacheMiss(requester);
         return action.get();
      } finally {
         this.providerPermits.release();
      }
   }

   private Duration positiveWindow() {
      Duration window = this.properties.getUserGenerationWindow();
      if (window != null && !window.isZero() && !window.isNegative()) {
         return window;
      } else {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_DISABLED, "画作导览生成功能当前未配置");
      }
   }

   private Duration positiveGlobalWindow() {
      Duration window = this.properties.getGlobalGenerationWindow();
      if (window != null && !window.isZero() && !window.isNegative()) {
         return window;
      } else {
         throw new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.GUIDE_DISABLED, "画作导览生成功能当前未配置");
      }
   }

   private String requesterKey(String requester) {
      if (requester != null && !requester.isBlank() && requester.length() <= 256) {
         try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(requester.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
         } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
         }
      } else {
         throw new ApiV1Exception(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, "需要身份验证");
      }
   }

   int trackedRequesterCount() {
      return this.usage.size();
   }

   int availableProviderPermits() {
      return this.providerPermits.availablePermits();
   }

   synchronized int globalUsageCount() {
      return this.globalUsage.size();
   }

   private void prune(ArrayDeque<Long> timestamps, long now, long windowMillis) {
      long cutoff = now - windowMillis;

      while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
         timestamps.removeFirst();
      }
   }
}

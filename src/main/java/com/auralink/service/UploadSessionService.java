package com.auralink.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class UploadSessionService {
   private static final Duration SESSION_TTL = Duration.ofMinutes(30L);
   private final Map<String, UploadSessionService.SessionData> sessionStore = new ConcurrentHashMap<>();

   public void save(String sessionId, String filepath) {
      this.cleanupExpired();
      this.sessionStore.put(sessionId, new UploadSessionService.SessionData(filepath, Instant.now()));
   }

   public String get(String sessionId) {
      this.cleanupExpired();
      UploadSessionService.SessionData data = this.sessionStore.get(sessionId);
      return data == null ? null : data.filepath();
   }

   private void cleanupExpired() {
      Instant now = Instant.now();
      this.sessionStore.entrySet().removeIf(entry -> Duration.between(entry.getValue().createdAt(), now).compareTo(SESSION_TTL) > 0);
   }

   private record SessionData(String filepath, Instant createdAt) {
   }
}

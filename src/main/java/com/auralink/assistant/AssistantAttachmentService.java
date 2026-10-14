package com.auralink.assistant;

import com.auralink.exception.StorageException;
import com.auralink.service.media.InvalidImageContentException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import static com.auralink.assistant.AssistantRecords.*;

/** No Critic/Creation writes, provider calls, automatic cleanup or shared media directory access. */
public final class AssistantAttachmentService {
   private final AssistantStore store;
   private final AssistantAttachmentStorage storage;
   private final Clock clock;

   public AssistantAttachmentService(AssistantStore store, AssistantAttachmentStorage storage, Clock clock) {
      this.store = store;
      this.storage = storage;
      this.clock = clock;
   }

   public Attachment upload(long owner, UUID sessionId, String requestKey, MultipartFile file) {
      // Reject missing/foreign sessions before consuming bytes. An owned tombstone is permitted only
      // for request-key classification below: a deleted replay stays 410 and changed payload stays 409.
      // The reserve operation still refuses every fresh request to a deleted session.
      store.requireOwnedSessionForRequest(owner, sessionId);
      if (file == null) throw AssistantFailure.invalid("An image is required");
      String filename = filename(file.getOriginalFilename());
      String mime = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if (!mime.equals("image/jpeg") && !mime.equals("image/png")) throw AssistantFailure.invalid("Only JPEG and PNG images are supported");
      byte[] bytes;
      try { bytes = storage.readUpload(file.getInputStream()); }
      catch (IOException | IllegalArgumentException exception) { throw AssistantFailure.invalid("Image is empty, unreadable or exceeds the configured limit"); }
      String digest = AssistantAttachmentStorage.sha256(bytes);
      // Length-prefixed metadata avoids ambiguous fingerprints; request keys are owner-bound by the store.
      String fingerprint = AssistantAttachmentStorage.sha256(("assistant-upload-v1\n" + sessionId + "\n"
         + filename.length() + ":" + filename + "\n" + mime + "\n" + bytes.length + "\n" + digest).getBytes(StandardCharsets.UTF_8));
      UUID proposedId = UUID.randomUUID();
      Attachment reserved = store.reserveAttachment(owner, sessionId, requestKey, fingerprint, proposedId,
         proposedId.toString(), filename, mime, bytes.length, digest, 0, 0);
      if (!reserved.id().equals(proposedId)) {
         if (reserved.status().equals("READY")) return store.attachment(owner, reserved.id());
         throw AssistantFailure.conflict("Attachment upload is already pending; recover its status instead of resending");
      }
      try {
         return store.withUploadingAttachment(owner, reserved.id(), metadata -> {
            requireStorageKey(metadata);
            storage.requireCapacity(bytes.length,store.storagePolicy().minimumFreeBytes());
            var image = storage.write(metadata.id(), bytes, metadata.sha256(), metadata.mimeType(), metadata.filename());
            return store.readyAttachment(owner, metadata.id(), image.width(), image.height());
         });
      } catch (RuntimeException failure) {
         // Best-effort state change only. On database outage the durable reservation remains UPLOADING,
         // and explicit cleanup can recover its known .part/.blob paths later.
         try { store.failAttachment(owner, reserved.id()); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
         if (failure instanceof InvalidImageContentException || failure instanceof IllegalArgumentException) {
            throw AssistantFailure.invalid("Image bytes, dimensions, MIME type or filename are invalid");
         }
         throw failure;
      }
   }

   public Attachment metadata(long owner, UUID id) { return store.attachment(owner, id); }

   public AttachmentContent content(long owner, UUID id) {
      return store.withAttachment(owner, id, metadata -> {
         requireStorageKey(metadata);
         return new AttachmentContent(metadata, storage.read(metadata.id(), metadata.byteSize(), metadata.sha256()));
      });
   }

   public void remove(long owner, UUID id) { store.removeAttachment(owner, id); }

   /** Explicit isolated/operational primitive only. No scheduled job or public administrative route. */
   public CleanupResult cleanup(Duration staleAge, int limit) {
      if (staleAge == null || staleAge.isNegative() || staleAge.isZero() || limit < 1 || limit > 100) {
         throw new IllegalArgumentException("Positive cleanup age and a bounded limit are required");
      }
      var staleBefore = clock.instant().minus(staleAge);
      int removed = 0;
      int failed = 0;
      for (Attachment candidate : store.cleanupCandidates(staleBefore, limit)) {
         try {
            boolean cleaned = store.cleanupAttachment(candidate.id(), staleBefore, metadata -> {
               requireStorageKey(metadata);
               storage.remove(metadata.id(), metadata.byteSize(), metadata.sha256());
               return true;
            });
            if (cleaned) removed++;
         } catch (RuntimeException exception) {
            // Refuse drift/symlinks/integrity mismatch and retain the durable record for investigation.
            failed++;
         }
      }
      return new CleanupResult(removed, failed);
   }


   public record StorageUsage(String policyStatus,boolean historyOnly,boolean uploadsAvailable,Long quotaBytes,long readyBytes,long reservedBytes,
      long logicalBytes,long cleanupReservedBytes,long chargedBytes,long retainedFileBytes,long pendingCleanupBytes,Long availableBytes,Long draftLifetimeSeconds,boolean cleanupEnabled) {}
   public StorageUsage usage(long owner) {
      var policy=store.storagePolicy();var accounting=store.storageAccounting(owner);long physical=0,pending=0;
      for(Attachment item:accounting.tracked()) {long bytes=storage.retainedBytes(item.id());physical=Math.addExact(physical,bytes);if(!item.status().equals("READY")&&!item.status().equals("UPLOADING"))pending=Math.addExact(pending,bytes);}
      long logical=Math.addExact(accounting.readyBytes(),accounting.reservedBytes());long charged=Math.addExact(logical,accounting.cleanupReservedBytes());Long available=policy.quotaBytes()==null?null:Math.max(0,policy.quotaBytes()-charged);
      return new StorageUsage(policy.status(),policy.historyOnly(),"VALID".equals(policy.status())&&!policy.historyOnly()&&available>0,policy.quotaBytes(),accounting.readyBytes(),accounting.reservedBytes(),logical,accounting.cleanupReservedBytes(),charged,physical,pending,available,policy.draftLifetime()==null?null:policy.draftLifetime().toSeconds(),policy.cleanupEnabled());
   }
   public record MaintenanceItem(UUID attachmentId,String initialState,String outcome) {}
   public record MaintenanceResult(boolean dryRun,int examined,int eligible,int removed,int refused,java.util.List<MaintenanceItem> items) {}
   /** Explicit operator invocation, no scheduler. Dry run and apply both recheck under lifecycle locks. */
   public MaintenanceResult maintain(boolean dryRun,int limit) {
      store.storagePolicy().requireCleanup();var items=new java.util.ArrayList<MaintenanceItem>();int eligible=0,removed=0,refused=0;
      for(Attachment candidate:store.retentionCandidates(limit)) {
         try {
            boolean accepted=store.retentionAttachment(candidate.id(),!dryRun,metadata->{requireStorageKey(metadata);if(dryRun)storage.inspectCleanup(metadata.id(),metadata.byteSize(),metadata.sha256());else storage.remove(metadata.id(),metadata.byteSize(),metadata.sha256());return true;});
            if(accepted){eligible++;if(!dryRun)removed++;}
            items.add(new MaintenanceItem(candidate.id(),candidate.status(),accepted?(dryRun?"ELIGIBLE":"REMOVED"):"STATE_CHANGED"));
         } catch(RuntimeException failure){refused++;items.add(new MaintenanceItem(candidate.id(),candidate.status(),"REFUSED"));}
      }
      return new MaintenanceResult(dryRun,items.size(),eligible,removed,refused,java.util.List.copyOf(items));
   }

   private static void requireStorageKey(Attachment metadata) {
      if (!metadata.id().toString().equals(metadata.storageKey())) {
         throw new StorageException("Assistant attachment storage identity is invalid");
      }
   }

   private static String filename(String supplied) {
      if (supplied == null || supplied.isBlank()) throw AssistantFailure.invalid("Image filename is required");
      String name = supplied.replace('\\', '/');
      name = name.substring(name.lastIndexOf('/') + 1).strip();
      if (name.isEmpty() || name.length() > 180 || name.chars().anyMatch(c -> c < 32 || c == 127)) {
         throw AssistantFailure.invalid("Image filename is invalid");
      }
      return name;
   }

   public record AttachmentContent(Attachment metadata, byte[] bytes) {}
   public record CleanupResult(int removed, int failed) {}
}

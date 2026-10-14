package com.auralink.assistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence values; storageKey is internal and must never be serialized as a public DTO. */
public final class AssistantRecords {
   private AssistantRecords() {}
   public record Session(UUID id,String title,UUID activeBranchId,long version,boolean summaryAutoEnabled,String status,Instant createdAt,Instant updatedAt) {}
   public record Branch(UUID id,UUID sessionId,UUID parentBranchId,UUID forkMessageId,UUID headMessageId,long version,Instant createdAt) {}
   public record Message(UUID id,UUID sessionId,UUID parentMessageId,UUID versionGroupId,UUID supersedesId,String role,String body,String state,UUID runId,Instant createdAt,List<UUID> attachmentIds) {}
   public record Run(UUID id,UUID sessionId,UUID branchId,String kind,String status,UUID inputMessageId,long fence,long sequence,String checkpoint,Instant createdAt,Instant updatedAt) {}
   public record Attachment(UUID id,UUID sessionId,String storageKey,String filename,String mimeType,long byteSize,String sha256,int width,int height,String status,Instant createdAt,Instant updatedAt) {}
   public record DraftAttachment(Attachment attachment,Instant expiresAt) {}
   public record AcceptedTurn(Message message,Run run,Branch branch,boolean replay) {}
   public record ExportSnapshot(Session session,List<Branch> branches,List<Message> messages,List<Run> runs,List<Attachment> attachments) {}
}

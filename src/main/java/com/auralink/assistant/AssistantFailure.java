package com.auralink.assistant;

/** Domain failures deliberately contain no SQL, paths, credentials or other owners' data. */
public final class AssistantFailure extends RuntimeException {
   private final int status;
   private final String code;
   public AssistantFailure(int status, String code, String message) { super(message); this.status=status; this.code=code; }
   public int status() { return status; }
   public String code() { return code; }
   public static AssistantFailure notFound() { return new AssistantFailure(404,"ASSISTANT_NOT_FOUND","Assistant object not found"); }
   public static AssistantFailure conflict(String message) { return new AssistantFailure(409,"ASSISTANT_CONFLICT",message); }
   public static AssistantFailure gone() { return new AssistantFailure(410,"ASSISTANT_REQUEST_GONE","The request result was deleted or expired; this key cannot be reused"); }
   public static AssistantFailure invalid(String message) { return new AssistantFailure(400,"ASSISTANT_INVALID_REQUEST",message); }
}

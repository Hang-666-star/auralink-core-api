package com.auralink.assistant;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/** Explicit operator policy. Parse failures disable only new attachment writes/maintenance. No production defaults. */
public record AssistantStoragePolicy(String status, Long quotaBytes, Duration draftLifetime, long minimumFreeBytes,
   boolean cleanupEnabled, boolean historyOnly, Duration staleUploadAge, Duration failedGrace, Duration deletedGrace) {
   public static AssistantStoragePolicy missing() { return new AssistantStoragePolicy("MISSING",null,null,0,false,false,null,null,null); }
   public static AssistantStoragePolicy read(Function<String,String> get) {
      String mode=get.apply("auralink.assistant.history-only");
      boolean historyOnly="true".equals(mode);
      if(mode!=null&&!mode.equals("true")&&!mode.equals("false"))return new AssistantStoragePolicy("INVALID",null,null,0,false,true,null,null,null);
      String prefix="auralink.assistant.storage.";
      String quota=get.apply(prefix+"quota-bytes"), draft=get.apply(prefix+"draft-lifetime"), free=get.apply(prefix+"minimum-free-bytes");
      if(quota==null&&draft==null&&free==null&&get.apply(prefix+"cleanup-enabled")==null)return new AssistantStoragePolicy("MISSING",null,null,0,false,historyOnly,null,null,null);
      try {
         if(quota==null||draft==null||free==null)return new AssistantStoragePolicy("MISSING",null,null,0,false,historyOnly,null,null,null);
         long maximum=Long.parseLong(quota), minimum=Long.parseLong(free);Duration lifetime=Duration.parse(draft);
         String enabled=get.apply(prefix+"cleanup-enabled");if(enabled!=null&&!enabled.equals("true")&&!enabled.equals("false"))throw new IllegalArgumentException();
         boolean cleanup="true".equals(enabled);Duration stale=null,failed=null,deleted=null;
         if(cleanup){stale=Duration.parse(get.apply(prefix+"stale-upload-age"));failed=Duration.parse(get.apply(prefix+"failed-grace"));deleted=Duration.parse(get.apply(prefix+"deleted-grace"));}
         if(maximum<1||maximum>9007199254740991L||minimum<0||!positive(lifetime)||(cleanup&&(!positive(stale)||!positive(failed)||!positive(deleted))))throw new IllegalArgumentException();
         return new AssistantStoragePolicy("VALID",maximum,lifetime,minimum,cleanup,historyOnly,stale,failed,deleted);
      } catch(RuntimeException invalid){return new AssistantStoragePolicy("INVALID",null,null,0,false,historyOnly,null,null,null);}
   }
   public static AssistantStoragePolicy from(Map<String,String> config) { return read(config::get); }
   private static boolean positive(Duration value){return value!=null&&value.getSeconds()>=1&&value.getNano()==0;}
   public void requireUploads(){if(historyOnly)throw new AssistantFailure(503,"ASSISTANT_HISTORY_ONLY","当前为历史只读模式；可查看、下载、导出或删除，不能上传新附件");if(!"VALID".equals(status))throw new AssistantFailure(503,"ASSISTANT_STORAGE_POLICY_UNAVAILABLE","附件存储策略尚未配置或无效；历史内容仍可读取、导出和删除");}
   public void requireCleanup(){if(!"VALID".equals(status))throw new AssistantFailure(503,"ASSISTANT_STORAGE_POLICY_UNAVAILABLE","Assistant storage policy is unavailable");if(!cleanupEnabled)throw new AssistantFailure(503,"ASSISTANT_CLEANUP_DISABLED","Assistant cleanup is not enabled by an explicit policy");}
}

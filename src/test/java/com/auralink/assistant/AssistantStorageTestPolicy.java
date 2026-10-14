package com.auralink.assistant;
import java.util.Map;
final class AssistantStorageTestPolicy {
 static AssistantStoragePolicy valid(){return policy(20*1024*1024);}
 static AssistantStoragePolicy policy(long quota){return AssistantStoragePolicy.from(Map.of(
  "auralink.assistant.storage.quota-bytes",Long.toString(quota),
  "auralink.assistant.storage.draft-lifetime","PT24H",
  "auralink.assistant.storage.minimum-free-bytes","0",
  "auralink.assistant.storage.cleanup-enabled","true",
  "auralink.assistant.storage.stale-upload-age","PT1H",
  "auralink.assistant.storage.failed-grace","PT1H",
  "auralink.assistant.storage.deleted-grace","PT1H"));}
}

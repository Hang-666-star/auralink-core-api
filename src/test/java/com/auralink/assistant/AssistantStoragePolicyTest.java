package com.auralink.assistant;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class AssistantStoragePolicyTest {
 @Test void absentMalformedAndIncompleteDoNotThrowOrEnableCleanup(){
  assertEquals("MISSING",AssistantStoragePolicy.from(Map.of()).status());
  var invalid=AssistantStoragePolicy.from(Map.of("auralink.assistant.storage.quota-bytes","oops","auralink.assistant.storage.draft-lifetime","PT1H","auralink.assistant.storage.minimum-free-bytes","0"));
  assertEquals("INVALID",invalid.status());assertFalse(invalid.cleanupEnabled());assertEquals(503,assertThrows(AssistantFailure.class,invalid::requireUploads).status());
  assertEquals("MISSING",AssistantStoragePolicy.from(Map.of("auralink.assistant.storage.quota-bytes","10")).status());
 }
 @Test void policyUsesExplicitBoundsAndNeverInventsTombstoneExpiry(){var policy=AssistantStorageTestPolicy.policy(100);assertEquals(100,policy.quotaBytes());assertTrue(policy.cleanupEnabled());assertEquals(86400,policy.draftLifetime().toSeconds());}
}

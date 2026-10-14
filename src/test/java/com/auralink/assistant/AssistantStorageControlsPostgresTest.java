package com.auralink.assistant;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSISTANT_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/assistant_foundation_test")
class AssistantStorageControlsPostgresTest {
 @TempDir Path directory;JdbcTemplate admin,jdbc;DriverManagerDataSource source;String schema;
 MutableClock clock;PostgresAssistantStore store;AssistantAttachmentStorage storage;AssistantAttachmentService service;
 static final class MutableClock extends Clock {Instant value=Instant.parse("2026-01-01T00:00:00Z");public Instant instant(){return value;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}void advance(Duration d){value=value.plus(d);}}
 @BeforeEach void setup()throws Exception{
  var url=System.getenv("ASSISTANT_TEST_JDBC_URL");var user=System.getenv("ASSISTANT_TEST_JDBC_USER");var pass=System.getenv("ASSISTANT_TEST_JDBC_PASSWORD");
  admin=new JdbcTemplate(new DriverManagerDataSource(url,user,pass));schema="s4_storage_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);source=new DriverManagerDataSource(url+"?currentSchema="+schema,user,pass);jdbc=new JdbcTemplate(source);
  jdbc.execute("CREATE TABLE catalog_users(id bigint PRIMARY KEY)");jdbc.execute("INSERT INTO catalog_users VALUES(1),(2)");
  for(String migration:List.of("V014__assistant_records.sql","V015__assistant_execution.sql","V016__assistant_storage_controls.sql"))jdbc.execute(Files.readString(Path.of("db/postgres/migrations",migration)));
  clock=new MutableClock();storage=new AssistantAttachmentStorage(directory.resolve("private"),1048576,32000000);configure(AssistantStorageTestPolicy.valid());
 }
 void configure(AssistantStoragePolicy policy){store=new PostgresAssistantStore(source,clock,Duration.ofDays(7),8,policy);service=new AssistantAttachmentService(store,storage,clock);}
 @AfterEach void cleanup(){if(admin!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE");}
 static MockMultipartFile image(byte[] bytes){return new MockMultipartFile("file","test.png","image/png",bytes);}
 AssistantRecords.Session session(String key){return store.createSession(1,key,"fixture");}
 @Test void concurrentQuotaAcrossSessionsAndDuplicateReplayAreAtomic()throws Exception{
  byte[] bytes=AssistantAttachmentStorageTest.png(2,2);configure(AssistantStorageTestPolicy.policy(bytes.length));var a=session("account-session-a");var b=session("account-session-b");
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{var results=new ArrayList<Future<Object>>();for(var s:List.of(a,b))results.add(pool.submit(()->{gate.await();try{return service.upload(1,s.id(),"upload-key-"+s.id(),image(bytes));}catch(AssistantFailure e){return e;}}));gate.countDown();int ready=0,denied=0;AssistantRecords.Attachment winner=null;
   for(var result:results){Object item=result.get(5,TimeUnit.SECONDS);if(item instanceof AssistantRecords.Attachment attachment){winner=attachment;ready++;}else{assertEquals(413,((AssistantFailure)item).status());denied++;}}
   assertEquals(1,ready);assertEquals(1,denied);assertNotNull(winner);assertEquals(winner.id(),service.upload(1,winner.sessionId(),"upload-key-"+winner.sessionId(),image(bytes)).id());
   var usage=service.usage(1);assertEquals(bytes.length,usage.logicalBytes());assertEquals(bytes.length,usage.retainedFileBytes());assertFalse(usage.uploadsAvailable());assertEquals(0,service.usage(2).logicalBytes());
   assertArrayEquals(bytes,service.content(1,winner.id()).bytes());assertNotNull(store.exportSnapshot(1,winner.sessionId(),false));service.remove(1,winner.id());assertEquals(0,service.usage(1).logicalBytes());assertEquals(bytes.length,service.usage(1).pendingCleanupBytes());assertEquals(bytes.length,service.usage(1).cleanupReservedBytes());assertEquals(0,service.usage(1).availableBytes());
   clock.advance(Duration.ofHours(2));assertEquals(1,service.maintain(false,10).removed());
   assertNotNull(service.upload(1,b.id(),"new-intent-after-delete",image(bytes)));
  }finally{pool.shutdownNow();}
 }
 @Test void reservationCountsUntilFailedOrExplicitRecoveryAndDiskFailureNeverReady()throws Exception{
  byte[] bytes=AssistantAttachmentStorageTest.png(2,2);configure(AssistantStorageTestPolicy.policy(bytes.length));var a=session("reservation-session");UUID id=UUID.randomUUID();store.reserveAttachment(1,a.id(),"reserved-request","a".repeat(64),id,id.toString(),"x.png","image/png",bytes.length,AssistantAttachmentStorage.sha256(bytes),0,0);
  assertEquals(bytes.length,service.usage(1).reservedBytes());assertEquals(413,assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"blocked-reservation",image(bytes))).status());
  Path part=directory.resolve("private").resolve(id+".part");Files.write(part,new byte[]{1,2,3});Files.setPosixFilePermissions(part,PosixFilePermissions.fromString("rw-------"));assertEquals(3,service.usage(1).retainedFileBytes());
  clock.advance(Duration.ofHours(2));assertEquals(1,service.maintain(true,5).eligible());assertTrue(Files.exists(part));assertEquals(1,service.maintain(false,5).removed());assertEquals(0,service.maintain(false,5).removed());assertEquals(0,service.usage(1).logicalBytes());assertThrows(AssistantFailure.class,()->store.readyAttachment(1,id,2,2));
  var valid=AssistantStorageTestPolicy.valid();configure(new AssistantStoragePolicy("VALID",valid.quotaBytes(),valid.draftLifetime(),Long.MAX_VALUE,false,false,null,null,null));
  assertEquals(507,assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"disk-full-request",image(bytes))).status());assertEquals(0,service.usage(1).reservedBytes());assertEquals(bytes.length,service.usage(1).cleanupReservedBytes());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assistant_attachments WHERE status='READY'",Integer.class));
 }

 @Test void failedUploadsCannotBypassPhysicalReservationQuota()throws Exception{
  byte[] invalid=new byte[]{1,2,3,4};configure(AssistantStorageTestPolicy.policy(invalid.length));var a=session("failed-quota-session");
  assertEquals(400,assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"first-invalid-image",image(invalid))).status());
  assertEquals(0,service.usage(1).logicalBytes());assertEquals(4,service.usage(1).chargedBytes());assertEquals(4,service.usage(1).retainedFileBytes());
  assertEquals(413,assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"second-invalid-image",image(invalid))).status());
  clock.advance(Duration.ofHours(2));assertEquals(1,service.maintain(false,10).removed());assertEquals(0,service.usage(1).chargedBytes());
 }

 @Test void readyDraftExpiresOnlyByServerStateWhileHistoricalAttachmentsRemain()throws Exception{
  var a=session("retention-session");byte[] bytes=AssistantAttachmentStorageTest.png(3,2);var sent=service.upload(1,a.id(),"sent-image-key",image(bytes));var draft=service.upload(1,a.id(),"draft-image-key",image(bytes));
  assertEquals(2,store.draftAttachmentPage(1,a.id(),10,null).items().size());var turn=store.acceptTurn(1,a.id(),a.activeBranchId(),0,"message-with-image","synthetic",List.of(sent.id()));
  clock.advance(Duration.ofHours(25));assertEquals(0,store.draftAttachmentPage(1,a.id(),10,null).items().size());var plan=service.maintain(true,10);assertEquals(1,plan.eligible());assertEquals(draft.id(),plan.items().get(0).attachmentId());
  assertEquals(1,service.maintain(false,10).removed());assertArrayEquals(bytes,service.content(1,sent.id()).bytes());assertEquals(List.of(sent.id()),store.history(1,a.id(),null,true,10).get(0).attachmentIds());
  assertEquals(404,assertThrows(AssistantFailure.class,()->service.content(1,draft.id())).status());assertEquals(410,assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"draft-image-key",image(bytes))).status());
 }
 @Test void cleanupRechecksHistoricalReferencesAndDeletedSessionCannotBeRevived()throws Exception{
  var a=session("recheck-session");byte[] bytes=AssistantAttachmentStorageTest.png(2,3);var attachment=service.upload(1,a.id(),"recheck-image-key",image(bytes));clock.advance(Duration.ofHours(25));var selected=store.retentionCandidates(10);assertEquals(1,selected.size());
  // A retained historical reference established before lock acquisition must win even if cleanup selected earlier.
  UUID message=UUID.randomUUID();jdbc.update("INSERT INTO assistant_messages(id,session_id,version_group_id,role,body,state,created_at) VALUES(?,?,?,'USER','historical','ACCEPTED',?)",message,a.id(),message,java.sql.Timestamp.from(clock.instant()));jdbc.update("INSERT INTO assistant_message_attachments VALUES(?,?,?,0)",a.id(),message,attachment.id());
  assertFalse(store.retentionAttachment(attachment.id(),true,item->{fail("Referenced file must not be removed");return true;}));assertArrayEquals(bytes,service.content(1,attachment.id()).bytes());
  store.deleteSession(1,a.id());assertEquals(404,assertThrows(AssistantFailure.class,()->service.content(1,attachment.id())).status());clock.advance(Duration.ofHours(2));assertEquals(1,service.maintain(false,10).removed());assertThrows(AssistantFailure.class,()->store.readyAttachment(1,attachment.id(),2,3));assertEquals(0,service.maintain(false,10).removed());
 }

 @Test void historyOnlyPreservesReadDeleteAndRejectsNewAttachment()throws Exception{
  var a=session("history-only-session");byte[] bytes=AssistantAttachmentStorageTest.png(1,1);var item=service.upload(1,a.id(),"history-only-upload",image(bytes));var p=AssistantStorageTestPolicy.valid();
  configure(new AssistantStoragePolicy(p.status(),p.quotaBytes(),p.draftLifetime(),p.minimumFreeBytes(),p.cleanupEnabled(),true,p.staleUploadAge(),p.failedGrace(),p.deletedGrace()));
  assertTrue(service.usage(1).historyOnly());assertFalse(service.usage(1).uploadsAvailable());assertArrayEquals(bytes,service.content(1,item.id()).bytes());
  assertEquals("ASSISTANT_HISTORY_ONLY",assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"history-only-new",image(bytes))).code());assertNotNull(store.exportSnapshot(1,a.id(),false));service.remove(1,item.id());
 }

 @Test void missingPolicyKeepsExistingReadsExportsAndDeletionAvailable()throws Exception{
  var a=session("policy-session");byte[] bytes=AssistantAttachmentStorageTest.png(1,1);var attachment=service.upload(1,a.id(),"policy-upload-key",image(bytes));configure(AssistantStoragePolicy.missing());
  assertEquals("MISSING",service.usage(1).policyStatus());assertFalse(service.usage(1).uploadsAvailable());assertArrayEquals(bytes,service.content(1,attachment.id()).bytes());assertNotNull(store.exportSnapshot(1,a.id(),false));assertEquals(503,assertThrows(AssistantFailure.class,()->service.upload(1,a.id(),"new-policy-upload",image(bytes))).status());store.deleteSession(1,a.id());assertThrows(AssistantFailure.class,()->service.content(1,attachment.id()));
 }

 @Test void concurrentSendCommitsReferenceBeforeCleanupCanDelete()throws Exception{
  var a=session("send-race-session");byte[] bytes=AssistantAttachmentStorageTest.png(2,3);var attachment=service.upload(1,a.id(),"send-race-image-key",image(bytes));
  var pool=Executors.newFixedThreadPool(2);var linked=new CountDownLatch(1);var release=new CountDownLatch(1);
  try {
   var sending=pool.submit(()->store.withAttachment(1,attachment.id(),metadata->{
    var accepted=store.acceptTurn(1,a.id(),a.activeBranchId(),0,"send-race-request","retained branch",List.of(attachment.id()));linked.countDown();
    try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("test release timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return accepted;
   }));
   assertTrue(linked.await(5,TimeUnit.SECONDS));clock.advance(Duration.ofHours(25));
   var cleaning=pool.submit(()->service.maintain(false,10));assertThrows(TimeoutException.class,()->cleaning.get(100,TimeUnit.MILLISECONDS));release.countDown();
   assertNotNull(sending.get(5,TimeUnit.SECONDS));assertEquals(0,cleaning.get(5,TimeUnit.SECONDS).removed());assertArrayEquals(bytes,service.content(1,attachment.id()).bytes());
  }finally{release.countDown();pool.shutdownNow();}
 }

 @Test void malformedForeignFilesAreRefusedAndOutsideNamespaceUntouched()throws Exception{
  var a=session("namespace-session");byte[] bytes=AssistantAttachmentStorageTest.png(1,1);var attachment=service.upload(1,a.id(),"namespace-upload",image(bytes));service.remove(1,attachment.id());clock.advance(Duration.ofHours(2));Path blob=directory.resolve("private").resolve(attachment.id()+".blob"),outside=directory.resolve("outside");Files.write(outside,new byte[]{9,8,7});Files.delete(blob);Files.createSymbolicLink(blob,outside);
  assertEquals(1,service.maintain(true,10).refused());assertTrue(Files.isSymbolicLink(blob));assertEquals(1,service.maintain(false,10).refused());assertArrayEquals(new byte[]{9,8,7},Files.readAllBytes(outside));assertEquals("DELETING",jdbc.queryForObject("SELECT status FROM assistant_attachments WHERE id=?",String.class,attachment.id()));
 }
}

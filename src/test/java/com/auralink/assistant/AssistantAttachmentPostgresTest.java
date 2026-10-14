package com.auralink.assistant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

/** Real disposable loopback PostgreSQL, synthetic accounts and Assistant-only filesystem. */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_TEST_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/assistant_foundation_test")
class AssistantAttachmentPostgresTest {
   @TempDir Path directory;
   private JdbcTemplate admin;
   private JdbcTemplate jdbc;
   private String schema;
   private DriverManagerDataSource source;
   private MutableClock clock;
   private PostgresAssistantStore store;
   private AssistantAttachmentStorage storage;
   private AssistantAttachmentService service;

   @BeforeEach void setup() throws Exception {
      String url = System.getenv("ASSISTANT_TEST_JDBC_URL");
      String user = System.getenv("ASSISTANT_TEST_JDBC_USER");
      String password = System.getenv("ASSISTANT_TEST_JDBC_PASSWORD");
      admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
      schema = "assistant_attachment_" + UUID.randomUUID().toString().replace("-", "");
      admin.execute("CREATE SCHEMA " + schema);
      source = new DriverManagerDataSource(url + "?currentSchema=" + schema, user, password);
      jdbc = new JdbcTemplate(source);
      jdbc.execute("CREATE TABLE catalog_users(id bigint PRIMARY KEY)");
      jdbc.execute("INSERT INTO catalog_users VALUES(1),(2)");
      jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V014__assistant_records.sql")));jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V016__assistant_storage_controls.sql")));
      clock = new MutableClock();
      store = new PostgresAssistantStore(source, clock, Duration.ofHours(24), 4,AssistantStorageTestPolicy.valid());
      storage = new AssistantAttachmentStorage(directory.resolve("assistant"), 1024 * 1024, 32_000_000);
      service = new AssistantAttachmentService(store, storage, clock);
   }

   @AfterEach void teardown() {
      if (admin != null && schema != null) admin.execute("DROP SCHEMA " + schema + " CASCADE");
   }

   @Test void actualImageOwnerIsolationIdempotencyAndSecondInstanceRecovery() throws Exception {
      var session = store.createSession(1, "create-session-A", "test");
      byte[] png = AssistantAttachmentStorageTest.png(5, 4);
      var attachment = service.upload(1, session.id(), "upload-image-key", file(png));
      assertEquals("READY", attachment.status());
      assertEquals(5, attachment.width());
      assertArrayEquals(png, service.content(1, attachment.id()).bytes());
      assertEquals(attachment.id(), service.upload(1, session.id(), "upload-image-key", file(png)).id());
      assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM assistant_attachments", Long.class));
      assertEquals(409, assertThrows(AssistantFailure.class,
         () -> service.upload(1, session.id(), "upload-image-key", file(AssistantAttachmentStorageTest.png(2, 2)))).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.metadata(2, attachment.id())).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.content(2, attachment.id())).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.remove(2, attachment.id())).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.upload(2, session.id(), "another-upload-key", file(png))).status());
      var restarted = new AssistantAttachmentService(new PostgresAssistantStore(source, clock, Duration.ofHours(24), 4,AssistantStorageTestPolicy.valid()),
         new AssistantAttachmentStorage(directory.resolve("assistant"), 1024 * 1024, 32_000_000), clock);
      assertArrayEquals(png, restarted.content(1, attachment.id()).bytes());
   }

   @Test void deletedSessionRevokesContentAndLateCompletionBeforeCleanup() throws Exception {
      var session = store.createSession(1, "delete-session-key", "test");
      byte[] png = AssistantAttachmentStorageTest.png(1, 1);
      var attachment = service.upload(1, session.id(), "delete-upload-key", file(png));
      var second = store.createSession(1, "second-session-key", "second");
      assertEquals(404, assertThrows(AssistantFailure.class,
         () -> store.acceptTurn(1, second.id(), second.activeBranchId(), second.version(), "cross-session-key", "test", List.of(attachment.id()))).status());
      store.deleteSession(1, session.id());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.content(1, attachment.id())).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.metadata(1, attachment.id())).status());
      assertThrows(AssistantFailure.class, () -> store.readyAttachment(1, attachment.id(), 1, 1));
      assertEquals(1, service.cleanup(Duration.ofHours(1), 10).removed());
      assertFalse(Files.exists(directory.resolve("assistant").resolve(attachment.id() + ".blob")));
      assertEquals("DELETED", jdbc.queryForObject("SELECT status FROM assistant_attachments WHERE id=?", String.class, attachment.id()));
      assertEquals(410, assertThrows(AssistantFailure.class, () -> service.upload(1, session.id(), "delete-upload-key", file(png))).status());
      assertEquals(409, assertThrows(AssistantFailure.class,
         () -> service.upload(1, session.id(), "delete-upload-key", file(AssistantAttachmentStorageTest.png(2, 2)))).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.upload(1, session.id(), "fresh-deleted-key", file(png))).status());
      assertEquals(404, assertThrows(AssistantFailure.class, () -> service.upload(2, session.id(), "delete-upload-key", file(png))).status());
   }

   @Test void interruptedReservationAndPartialFileRecoverOnlyInOwnNamespace() throws Exception {
      var session = store.createSession(1, "partial-session-key", "test");
      byte[] png = AssistantAttachmentStorageTest.png(2, 2);
      UUID id = UUID.randomUUID();
      store.reserveAttachment(1, session.id(), "partial-upload-key", "a".repeat(64), id, id.toString(),
         "test.png", "image/png", png.length, AssistantAttachmentStorage.sha256(png), 0, 0);
      Path partial = directory.resolve("assistant").resolve(id + ".part");
      Files.write(partial, new byte[]{1, 2});
      Files.setPosixFilePermissions(partial, PosixFilePermissions.fromString("rw-------"));
      Path unrelated = Files.write(directory.resolve("outside"), new byte[]{9, 8});
      assertEquals(0, service.cleanup(Duration.ofHours(1), 10).removed());
      clock.advance(Duration.ofHours(2));
      assertEquals(1, service.cleanup(Duration.ofHours(1), 10).removed());
      assertFalse(Files.exists(partial));
      assertArrayEquals(new byte[]{9, 8}, Files.readAllBytes(unrelated));
      assertEquals("DELETED", jdbc.queryForObject("SELECT status FROM assistant_attachments WHERE id=?", String.class, id));
      assertThrows(AssistantFailure.class, () -> store.readyAttachment(1, id, 2, 2));
   }

   @Test void concurrentDeleteWaitsForUploadThenPreventsLateWriteAndCleansBytes() throws Exception {
      var session = store.createSession(1, "race-session-key", "test");
      byte[] png = AssistantAttachmentStorageTest.png(2, 2);
      UUID id = UUID.randomUUID();
      String sha = AssistantAttachmentStorage.sha256(png);
      store.reserveAttachment(1, session.id(), "race-upload-key", "b".repeat(64), id, id.toString(), "test.png", "image/png", png.length, sha, 0, 0);
      var pool = Executors.newFixedThreadPool(2);
      var writeLocked = new CountDownLatch(1);
      var releaseWrite = new CountDownLatch(1);
      try {
         var uploading = pool.submit(() -> store.withUploadingAttachment(1, id, metadata -> {
            writeLocked.countDown();
            try { assertTrue(releaseWrite.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException exception) { throw new RuntimeException(exception); }
            storage.write(id, png, sha, "image/png", "test.png");
            return store.readyAttachment(1, id, 2, 2);
         }));
         assertTrue(writeLocked.await(10, TimeUnit.SECONDS));
         var deleting = pool.submit(() -> store.deleteSession(1, session.id()));
         assertThrows(java.util.concurrent.TimeoutException.class, () -> deleting.get(100, TimeUnit.MILLISECONDS));
         releaseWrite.countDown();
         assertEquals("READY", uploading.get(10, TimeUnit.SECONDS).status());
         deleting.get(10, TimeUnit.SECONDS);
         assertThrows(AssistantFailure.class, () -> store.withUploadingAttachment(1, id, metadata -> { fail("Late content write must never run"); return null; }));
         assertEquals(1, service.cleanup(Duration.ofHours(1), 10).removed());
         assertFalse(Files.exists(directory.resolve("assistant").resolve(id + ".blob")));
         assertEquals(404, assertThrows(AssistantFailure.class, () -> service.content(1, id)).status());
      } finally { releaseWrite.countDown(); pool.shutdownNow(); }
   }

   @Test void interruptedDatabaseCompletionLeavesTrackedBlobRecoverable() throws Exception {
      var session = store.createSession(1, "interrupted-session", "test");
      byte[] png = AssistantAttachmentStorageTest.png(2, 2);
      UUID id = UUID.randomUUID();
      String sha = AssistantAttachmentStorage.sha256(png);
      store.reserveAttachment(1, session.id(), "interrupted-upload", "c".repeat(64), id, id.toString(), "test.png", "image/png", png.length, sha, 0, 0);
      assertThrows(IllegalStateException.class, () -> store.withUploadingAttachment(1, id, metadata -> {
         storage.write(id, png, sha, "image/png", "test.png");
         store.readyAttachment(1, id, 2, 2);
         throw new IllegalStateException("injected interruption before transaction commit");
      }));
      assertEquals("UPLOADING", jdbc.queryForObject("SELECT status FROM assistant_attachments WHERE id=?", String.class, id));
      assertTrue(Files.exists(directory.resolve("assistant").resolve(id + ".blob")));
      assertThrows(AssistantFailure.class, () -> service.content(1, id));
      clock.advance(Duration.ofHours(2));
      assertEquals(1, service.cleanup(Duration.ofHours(1), 10).removed());
      assertFalse(Files.exists(directory.resolve("assistant").resolve(id + ".blob")));
      assertThrows(AssistantFailure.class, () -> store.readyAttachment(1, id, 2, 2));
   }

   @Test void draftRecoveryRequiresLiveAcceptedUploadAndExcludesHistoryRemovedFailedAndOrphans() throws Exception {
      var session=store.createSession(1,"draft-session-key","draft");
      byte[] png=AssistantAttachmentStorageTest.png(2,2);
      var kept=service.upload(1,session.id(),"draft-upload-one",file(png));
      clock.advance(Duration.ofSeconds(1));
      var second=service.upload(1,session.id(),"draft-upload-two",file(png));
      clock.advance(Duration.ofSeconds(1));
      var linked=service.upload(1,session.id(),"draft-upload-linked",file(png));
      store.acceptTurn(1,session.id(),session.activeBranchId(),session.version(),"draft-sent-turn","saved",List.of(linked.id()));
      var removed=service.upload(1,session.id(),"draft-upload-removed",file(png));
      service.remove(1,removed.id());
      var orphan=service.upload(1,session.id(),"draft-upload-orphan",file(png));
      jdbc.update("DELETE FROM assistant_requests WHERE result_id=?",orphan.id()); // Explicit old orphan fixture; never swept into a draft.
      UUID failed=UUID.randomUUID();
      store.reserveAttachment(1,session.id(),"draft-upload-failed","a".repeat(64),failed,failed.toString(),"test.png","image/png",png.length,AssistantAttachmentStorage.sha256(png),0,0);
      store.failAttachment(1,failed);
      var first=store.draftAttachmentPage(1,session.id(),1,null);
      assertEquals(kept.id(),first.items().get(0).attachment().id());assertTrue(first.hasMore());
      assertEquals(kept.createdAt().plus(Duration.ofHours(24)),first.items().get(0).expiresAt());
      var page=store.draftAttachmentPage(1,session.id(),1,first.nextCursor());
      assertEquals(List.of(second.id()),page.items().stream().map(d->d.attachment().id()).toList());assertFalse(page.hasMore());
      assertEquals(404,assertThrows(AssistantFailure.class,()->store.draftAttachmentPage(2,session.id(),10,null)).status());
      var other=store.createSession(1,"draft-other-session","other");
      assertEquals(400,assertThrows(AssistantFailure.class,()->store.draftAttachmentPage(1,other.id(),1,first.nextCursor())).status());
      var restarted=new PostgresAssistantStore(source,clock,Duration.ofHours(24),4,AssistantStorageTestPolicy.valid());
      assertEquals(2,restarted.draftAttachmentPage(1,session.id(),10,null).items().size());
      assertArrayEquals(png,service.content(1,kept.id()).bytes());
      assertArrayEquals(png,service.content(1,linked.id()).bytes());
      clock.advance(Duration.ofHours(25));
      assertTrue(store.draftAttachmentPage(1,session.id(),10,null).items().isEmpty());
      assertArrayEquals(png,service.content(1,linked.id()).bytes()); // Draft expiry does not erase retained history or image bytes.
      assertEquals(410,assertThrows(AssistantFailure.class,()->store.resolveRequest(1,"UPLOAD_ATTACHMENT","draft-upload-one",session.id())).status());
      store.deleteSession(1,session.id());
      assertEquals(404,assertThrows(AssistantFailure.class,()->store.draftAttachmentPage(1,session.id(),10,null)).status());
   }

   @Test void uploadResolutionDistinguishesPendingFailedReadyAndLinkedWithoutResending() throws Exception {
      var session=store.createSession(1,"resolve-session-key","resolve");
      byte[] png=AssistantAttachmentStorageTest.png(2,2);UUID id=UUID.randomUUID();
      var absent=store.resolveRequest(1,"UPLOAD_ATTACHMENT","resolve-upload-key",session.id());assertEquals("NOT_FOUND",absent.status());
      store.reserveAttachment(1,session.id(),"resolve-upload-key","b".repeat(64),id,id.toString(),"test.png","image/png",png.length,AssistantAttachmentStorage.sha256(png),0,0);
      var uploading=store.resolveRequest(1,"UPLOAD_ATTACHMENT","resolve-upload-key",session.id());
      assertEquals("UPLOADING",uploading.resultStatus());assertEquals(id,uploading.resultId());assertFalse(uploading.attachmentLinked());
      assertTrue(store.draftAttachmentPage(1,session.id(),10,null).items().isEmpty());
      store.failAttachment(1,id);
      assertEquals("FAILED",store.resolveRequest(1,"UPLOAD_ATTACHMENT","resolve-upload-key",session.id()).resultStatus());
      UUID retry=UUID.randomUUID();
      assertEquals(409,assertThrows(AssistantFailure.class,()->store.reserveAttachment(1,session.id(),"resolve-upload-key","b".repeat(64),retry,retry.toString(),"test.png","image/png",png.length,AssistantAttachmentStorage.sha256(png),0,0)).status());
      assertEquals(404,assertThrows(AssistantFailure.class,()->store.resolveRequest(2,"UPLOAD_ATTACHMENT","resolve-upload-key",session.id())).status());
      service.remove(1,id);
      assertEquals(410,assertThrows(AssistantFailure.class,()->store.resolveRequest(1,"UPLOAD_ATTACHMENT","resolve-upload-key",session.id())).status());
      var ready=service.upload(1,session.id(),"resolve-new-intent",file(png));
      var resolution=store.resolveRequest(1,"UPLOAD_ATTACHMENT","resolve-new-intent",session.id());
      assertEquals("READY",resolution.resultStatus());assertFalse(resolution.attachmentLinked());assertEquals(ready.id(),resolution.resultId());
      store.acceptTurn(1,session.id(),session.activeBranchId(),session.version(),"resolve-message-key","sent",List.of(ready.id()));
      assertTrue(store.resolveRequest(1,"UPLOAD_ATTACHMENT","resolve-new-intent",session.id()).attachmentLinked());
      assertTrue(store.draftAttachmentPage(1,session.id(),10,null).items().isEmpty());
      assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM assistant_attachments",Long.class));
      assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM assistant_runs",Long.class));
      assertArrayEquals(png,service.content(1,ready.id()).bytes());
   }

   @Test void explicitlyRemovingPendingUploadRejectsLateWriterAndCompletionWithoutRevivingDraft() throws Exception {
      var session=store.createSession(1,"remove-pending-session","test");
      byte[] png=AssistantAttachmentStorageTest.png(2,2);UUID id=UUID.randomUUID();String sha=AssistantAttachmentStorage.sha256(png);
      store.reserveAttachment(1,session.id(),"remove-pending-upload","e".repeat(64),id,id.toString(),"test.png","image/png",png.length,sha,0,0);
      assertEquals("UPLOADING",store.resolveRequest(1,"UPLOAD_ATTACHMENT","remove-pending-upload",session.id()).resultStatus());
      service.remove(1,id);
      assertEquals("ACTIVE",store.session(1,session.id()).status()); // Only this upload was removed.
      assertEquals("DELETING",jdbc.queryForObject("SELECT status FROM assistant_attachments WHERE id=?",String.class,id));
      assertEquals(410,assertThrows(AssistantFailure.class,()->store.resolveRequest(1,"UPLOAD_ATTACHMENT","remove-pending-upload",session.id())).status());
      assertEquals(404,assertThrows(AssistantFailure.class,()->store.withUploadingAttachment(1,id,metadata->{
         fail("A late worker must never enter the storage write callback after removal");return null;
      })).status());
      assertEquals(409,assertThrows(AssistantFailure.class,()->store.readyAttachment(1,id,2,2)).status());
      assertEquals(404,assertThrows(AssistantFailure.class,()->service.metadata(1,id)).status());
      assertEquals(404,assertThrows(AssistantFailure.class,()->service.content(1,id)).status());
      assertTrue(store.draftAttachmentPage(1,session.id(),10,null).items().isEmpty());
      assertFalse(Files.exists(directory.resolve("assistant").resolve(id+".blob")));
      assertFalse(Files.exists(directory.resolve("assistant").resolve(id+".part")));
      assertEquals("DELETING",jdbc.queryForObject("SELECT status FROM assistant_attachments WHERE id=?",String.class,id));
      assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM assistant_runs",Long.class));
   }

   private static MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "test.png", "image/png", bytes); }
   private static final class MutableClock extends Clock {
      private volatile Instant now = Instant.parse("2030-01-01T00:00:00Z");
      void advance(Duration duration) { now = now.plus(duration); }
      @Override public ZoneId getZone() { return ZoneOffset.UTC; }
      @Override public Clock withZone(ZoneId zone) { return this; }
      @Override public Instant instant() { return now; }
   }
}

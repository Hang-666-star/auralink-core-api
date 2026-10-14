package com.auralink.assistant;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Real PostgreSQL only: caller explicitly supplies a disposable loopback fixture. */
@EnabledIfEnvironmentVariable(named="ASSISTANT_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/assistant_foundation_test")
class AssistantStorePostgresTest {
   JdbcTemplate admin,jdbc;
   PostgresAssistantStore store;
   String schema;
   MutableClock clock;
   static final class MutableClock extends Clock {
      Instant value=Instant.parse("2026-01-01T00:00:00Z");
      @Override public ZoneId getZone(){return ZoneOffset.UTC;}
      @Override public Clock withZone(ZoneId zone){return this;}
      @Override public Instant instant(){return value;}
   }
   @BeforeEach void setup()throws Exception{
      String url=System.getenv("ASSISTANT_TEST_JDBC_URL"),user=System.getenv("ASSISTANT_TEST_JDBC_USER"),password=System.getenv("ASSISTANT_TEST_JDBC_PASSWORD");
      admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
      schema="assistant_store_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
      var source=new DriverManagerDataSource(url+"?currentSchema="+schema,user,password);jdbc=new JdbcTemplate(source);
      jdbc.execute("CREATE TABLE catalog_users(id bigint PRIMARY KEY)");jdbc.update("INSERT INTO catalog_users VALUES(1),(2)");
      jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V014__assistant_records.sql")));jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V016__assistant_storage_controls.sql")));
      clock=new MutableClock();store=new PostgresAssistantStore(source,clock,Duration.ofHours(24),2,AssistantStorageTestPolicy.valid());
   }
   @AfterEach void cleanup(){if(admin!=null&&schema!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE");}
   private AssistantFailure fails(int status,Runnable action){var e=assertThrows(AssistantFailure.class,action::run);assertEquals(status,e.status());return e;}
   @Test void accountScopeAndCrossSessionReferencesAreEnforced(){
      var a=store.createSession(1,"session-a","A");var b=store.createSession(2,"session-b","B");var c=store.createSession(1,"session-c","C");
      assertEquals(2,store.sessions(1,20,0).size());fails(404,()->store.session(2,a.id()));fails(404,()->store.updateSession(2,a.id(),a.version(),"stolen",null,null));
      fails(404,()->store.deleteSession(2,a.id()));fails(404,()->store.exportSnapshot(2,a.id(),true));fails(404,()->store.history(2,a.id(),null,true,100));
      fails(404,()->store.updateSession(1,a.id(),a.version(),null,b.activeBranchId(),null));fails(404,()->store.acceptTurn(1,a.id(),c.activeBranchId(),0,"turn-foreign","foreign",List.of()));
      assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE assistant_sessions SET active_branch_id=? WHERE id=?",c.activeBranchId(),a.id()));
      assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO assistant_requests(owner_id,operation,key_hash,payload_hash,session_id,status,created_at,expires_at) VALUES(2,'INVALID',?,?,?,'ACTIVE',now(),now()+interval '1 day')","a".repeat(64),"b".repeat(64),a.id()));
      var turn=store.acceptTurn(1,a.id(),a.activeBranchId(),0,"integrity-turn","private",List.of());
      assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO assistant_messages(id,session_id,version_group_id,role,body,state,created_at) VALUES(?,?,?,'USER','invalid','ACCEPTED',now())",UUID.randomUUID(),c.id(),turn.message().id()));
      assertEquals("A",store.session(1,a.id()).title());
   }
   @Test void idempotencySurvivesExpiryAndDeletionWithoutKeyReuse(){
      var a=store.createSession(1,"same-request","A");assertEquals(a.id(),store.createSession(1,"same-request","A").id());
      fails(409,()->store.createSession(1,"same-request","different"));
      clock.value=clock.value.plus(Duration.ofDays(2));fails(410,()->store.createSession(1,"same-request","A"));fails(409,()->store.createSession(1,"same-request","different"));
      assertEquals(1,store.sessions(1,20,0).size());assertNull(jdbc.queryForObject("SELECT result_id FROM assistant_requests WHERE operation='CREATE_SESSION'",UUID.class));
      var b=store.createSession(1,"delete-key","B");store.deleteSession(1,b.id());fails(410,()->store.createSession(1,"delete-key","B"));fails(404,()->store.session(1,b.id()));
   }
   @Test void concurrentIdenticalCreateReturnsExactlyOneSession()throws Exception{
      var executor=Executors.newFixedThreadPool(2);try{
         var gate=new CountDownLatch(1);var first=executor.submit(()->{gate.await();return store.createSession(1,"race-create","race").id();});var second=executor.submit(()->{gate.await();return store.createSession(1,"race-create","race").id();});gate.countDown();
         assertEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));assertEquals(1,store.sessions(1,20,0).size());
      }finally{executor.shutdownNow();}
   }
   @Test void summaryPendingDoesNotOccupyExecutionSlotOrBlockNewChat(){
      var s=store.createSession(1,"summary-session","summary");var initial=store.acceptTurn(1,s.id(),s.activeBranchId(),0,"initial-turn","question",List.of());
      var chat=store.claimNext(1,s.id()).orElseThrow();store.finish(1,chat.id(),chat.fence(),"COMPLETED","answer");
      var oldSummary=store.enqueueSummary(1,s.id(),s.activeBranchId(),"summary-one");assertEquals("QUEUED",oldSummary.status());
      assertEquals(oldSummary.id(),store.enqueueSummary(1,s.id(),s.activeBranchId(),"summary-two").id());
      var branch=store.branches(1,s.id()).get(0);var next=store.acceptTurn(1,s.id(),branch.id(),branch.version(),"next-user-turn","priority chat",List.of());
      assertEquals("CANCELLED",store.run(1,oldSummary.id()).status());assertEquals(next.run().id(),store.claimNext(1,s.id()).orElseThrow().id());assertTrue(store.claimNext(1,s.id()).isEmpty());
      assertTrue(store.acceptTurn(1,s.id(),branch.id(),branch.version(),"next-user-turn","priority chat",List.of()).replay());
      fails(409,()->store.acceptTurn(1,s.id(),branch.id(),branch.version(),"next-user-turn","changed",List.of()));
      assertEquals(initial.message().id(),store.history(1,s.id(),null,false,100).get(0).id());
   }
   @Test void executingSummaryIsNotCancelledOrResentWhenChatArrives(){
      var s=store.createSession(1,"active-summary","summary");store.acceptTurn(1,s.id(),s.activeBranchId(),0,"first-user","q",List.of());
      var first=store.claimNext(1,s.id()).orElseThrow();store.finish(1,first.id(),first.fence(),"COMPLETED","a");
      store.enqueueSummary(1,s.id(),s.activeBranchId(),"queued-summary");var summary=store.claimNext(1,s.id()).orElseThrow();
      var b=store.branches(1,s.id()).get(0);var chat=store.acceptTurn(1,s.id(),b.id(),b.version(),"next-chat","q2",List.of());
      assertEquals("EXECUTING",store.run(1,summary.id()).status());assertTrue(store.claimNext(1,s.id()).isEmpty());
      store.finish(1,summary.id(),summary.fence(),"COMPLETED","old context summary");assertEquals(chat.run().id(),store.claimNext(1,s.id()).orElseThrow().id());
   }
   @Test void editsCreateBranchAndKeepOldFutureOutOfCurrentHistory(){
      var s=store.createSession(1,"edit-session","edit");var t=store.acceptTurn(1,s.id(),s.activeBranchId(),0,"edit-turn","original",List.of());
      var run=store.claimNext(1,s.id()).orElseThrow();store.finish(1,run.id(),run.fence(),"COMPLETED","original future");
      var branch=store.branches(1,s.id()).get(0);var revised=store.reviseMessage(1,s.id(),branch.id(),t.message().id(),branch.version(),"edit-request","revised");
      assertEquals(List.of("revised"),store.history(1,s.id(),null,false,100).stream().map(AssistantRecords.Message::body).toList());
      assertEquals(3,store.history(1,s.id(),null,true,100).size());assertEquals("original future",store.history(1,s.id(),branch.id(),false,1).get(0).body());assertEquals(t.message().id(),store.history(1,s.id(),null,false,100).get(0).supersedesId());assertEquals(2,store.branches(1,s.id()).size());
      assertEquals(revised.id(),store.reviseMessage(1,s.id(),branch.id(),t.message().id(),branch.version(),"edit-request","revised").id());
   }
   @Test void tombstoneRevokesReadsAndLateCheckpointCompletionCannotRestoreText(){
      var s=store.createSession(1,"delete-session","delete");store.acceptTurn(1,s.id(),s.activeBranchId(),0,"delete-turn","sensitive",List.of());var run=store.claimNext(1,s.id()).orElseThrow();
      store.checkpoint(1,run.id(),run.fence(),0,"partial");store.deleteSession(1,s.id());
      fails(404,()->store.checkpoint(1,run.id(),run.fence(),1,"late"));fails(404,()->store.finish(1,run.id(),run.fence(),"COMPLETED","late"));fails(404,()->store.exportSnapshot(1,s.id(),true));
      assertEquals("",jdbc.queryForObject("SELECT body FROM assistant_messages WHERE session_id=?",String.class,s.id()));assertEquals("",jdbc.queryForObject("SELECT checkpoint FROM assistant_runs WHERE id=?",String.class,run.id()));
      assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM assistant_runs WHERE id=?",String.class,run.id()));
   }
   @Test void deletionWaitsForLockedExportAndNoLaterExportIsAllowed()throws Exception{
      var s=store.createSession(1,"export-session","export");var inExport=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
      try{var export=executor.submit(()->store.withExportSnapshot(1,s.id(),false,snapshot->{inExport.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return snapshot.session().id();}));
         assertTrue(inExport.await(5,TimeUnit.SECONDS));var delete=executor.submit(()->store.deleteSession(1,s.id()));Thread.sleep(150);assertFalse(delete.isDone());release.countDown();assertEquals(s.id(),export.get(5,TimeUnit.SECONDS));delete.get(5,TimeUnit.SECONDS);fails(404,()->store.withExportSnapshot(1,s.id(),false,x->x));
      }finally{release.countDown();executor.shutdownNow();}
   }
   @Test void interruptedExecutionCannotBeClaimedAgainOrCompletedByStaleWorker(){
      var s=store.createSession(1,"interrupt-session","interrupt");var turn=store.acceptTurn(1,s.id(),s.activeBranchId(),0,"interrupt-turn","q",List.of());var run=store.claimNext(1,s.id()).orElseThrow();
      store.interruptExecuting(1,s.id());assertEquals("INTERRUPTED",store.run(1,run.id()).status());assertTrue(store.claimNext(1,s.id()).isEmpty());fails(409,()->store.finish(1,run.id(),run.fence(),"COMPLETED","late"));
      assertEquals(run.id(),store.acceptTurn(1,s.id(),s.activeBranchId(),0,"interrupt-turn","q",List.of()).run().id());assertTrue(turn.run().id().equals(run.id()));
   }
   @Test void deletedTurnReplayIsGoneButDifferentPayloadRemainsConflict(){
      var s=store.createSession(1,"deleted-request-session","deleted");store.acceptTurn(1,s.id(),s.activeBranchId(),0,"deleted-request-turn","question",List.of());store.deleteSession(1,s.id());
      fails(410,()->store.acceptTurn(1,s.id(),s.activeBranchId(),0,"deleted-request-turn","question",List.of()));
      fails(409,()->store.acceptTurn(1,s.id(),s.activeBranchId(),0,"deleted-request-turn","different",List.of()));
   }
   @Test void switchingBranchesInvalidatesPendingSummaryAndHistoryValidatesBranchEvenForAllScope(){
      var s=store.createSession(1,"switch-session","switch");var t=store.acceptTurn(1,s.id(),s.activeBranchId(),0,"switch-turn","first",List.of());var r=store.claimNext(1,s.id()).orElseThrow();store.finish(1,r.id(),r.fence(),"COMPLETED","answer");
      var b=store.branches(1,s.id()).get(0);store.reviseMessage(1,s.id(),b.id(),t.message().id(),b.version(),"switch-revise","revision");
      var current=store.session(1,s.id());var summary=store.enqueueSummary(1,s.id(),current.activeBranchId(),"switch-summary");
      store.updateSession(1,s.id(),current.version(),null,b.id(),null);assertEquals("CANCELLED",store.run(1,summary.id()).status());
      var other=store.createSession(1,"switch-other","other");fails(404,()->store.history(1,s.id(),other.activeBranchId(),true,100));
   }
   @Test void concurrentDeleteAndCheckpointCannotReviveContent()throws Exception{
      var s=store.createSession(1,"late-write-session","race");store.acceptTurn(1,s.id(),s.activeBranchId(),0,"late-write-turn","secret",List.of());var r=store.claimNext(1,s.id()).orElseThrow();
      var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
      try{var delete=executor.submit(()->{start.await();store.deleteSession(1,s.id());return true;});var write=executor.submit(()->{start.await();try{store.checkpoint(1,r.id(),r.fence(),0,"late content");return true;}catch(AssistantFailure e){assertEquals(404,e.status());return false;}});start.countDown();delete.get(5,TimeUnit.SECONDS);write.get(5,TimeUnit.SECONDS);
         assertEquals("DELETED",jdbc.queryForObject("SELECT status FROM assistant_sessions WHERE id=?",String.class,s.id()));assertEquals("",jdbc.queryForObject("SELECT checkpoint FROM assistant_runs WHERE id=?",String.class,r.id()));
         fails(404,()->store.finish(1,r.id(),r.fence(),"COMPLETED","cannot resurrect"));
      }finally{executor.shutdownNow();}
   }

   @Test void enabledSchemaVerificationIsReadOnlyAndRefusesMissingSchedulingConstraint(){
      store.verifySchema();assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assistant_sessions",Integer.class));
      jdbc.execute("DROP INDEX assistant_one_executing");var failure=fails(503,store::verifySchema);assertEquals("ASSISTANT_SCHEMA_UNAVAILABLE",failure.code());
   }

}

package com.auralink.assistant;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** S3 read-only browsing against real disposable PostgreSQL; fixtures never dispatch. */
@EnabledIfEnvironmentVariable(named="ASSISTANT_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/assistant_foundation_test")
class AssistantPaginationPostgresTest {
 JdbcTemplate admin,jdbc;String schema;PostgresAssistantStore base;AssistantExecutionStore execution;
 final Instant epoch=Instant.parse("2026-10-01T00:00:00Z");
 @BeforeEach void setup()throws Exception{
  String url=System.getenv("ASSISTANT_TEST_JDBC_URL"),user=System.getenv("ASSISTANT_TEST_JDBC_USER"),password=System.getenv("ASSISTANT_TEST_JDBC_PASSWORD");
  admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));schema="s3_pages_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
  var source=new DriverManagerDataSource(url+"?currentSchema="+schema,user,password);jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TABLE catalog_users(id bigint PRIMARY KEY)");jdbc.update("INSERT INTO catalog_users VALUES(1),(2)");
  jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V014__assistant_records.sql")));jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V016__assistant_storage_controls.sql")));jdbc.execute(Files.readString(Path.of("db/postgres/migrations/V015__assistant_execution.sql")));
  base=new PostgresAssistantStore(source,Clock.fixed(epoch,ZoneOffset.UTC),Duration.ofDays(1),8);execution=new AssistantExecutionStore(source,base,Clock.fixed(epoch,ZoneOffset.UTC),Duration.ofDays(1),8,100);
 }
 @AfterEach void cleanup(){if(admin!=null&&schema!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE");}
 AssistantRecords.Session session(long owner){return base.createSession(owner,"session-"+UUID.randomUUID(),"synthetic history");}
 void failure(int status,Runnable action){assertEquals(status,assertThrows(AssistantFailure.class,action::run).status());}
 List<UUID> seedMessages(AssistantRecords.Session session,int count){
  var ids=new ArrayList<UUID>();var values=new ArrayList<Object[]>();UUID previous=null;
  for(int i=0;i<count;i++){UUID id=UUID.randomUUID();ids.add(id);values.add(new Object[]{id,session.id(),previous,id,"fixture "+i,Timestamp.from(epoch.plusMillis(i))});previous=id;}
  jdbc.batchUpdate("INSERT INTO assistant_messages(id,session_id,parent_message_id,version_group_id,role,body,state,created_at) VALUES(?,?,?,?,'USER',?,'ACCEPTED',?)",values);
  jdbc.update("UPDATE assistant_branches SET head_message_id=? WHERE id=?",previous,session.activeBranchId());return ids;
 }
 @Test void sessionAndBranchPagesHaveStableKeysetsAndOwnerFiltering(){
  var sessions=new HashSet<UUID>();for(int i=0;i<24;i++)sessions.add(session(1).id());session(2);
  var first=base.sessionPage(1,7,null);assertEquals(7,first.items().size());assertTrue(first.hasMore());var touched=first.items().get(0);base.updateSession(1,touched.id(),0,"renamed",null,null);
  var all=new ArrayList<>(first.items());String cursor=first.nextCursor();while(cursor!=null){var page=base.sessionPage(1,7,cursor);all.addAll(page.items());cursor=page.nextCursor();}
  assertEquals(sessions,new HashSet<>(all.stream().map(AssistantRecords.Session::id).toList()));assertEquals(24,all.size());
  var session=all.get(0);for(int i=0;i<15;i++)jdbc.update("INSERT INTO assistant_branches(id,session_id,created_at) VALUES(?,?,?)",UUID.randomUUID(),session.id(),Timestamp.from(epoch.plusSeconds(i+1)));
  var branches=new HashSet<UUID>();cursor=null;do{var page=base.branchPage(1,session.id(),5,cursor);page.items().forEach(b->assertTrue(branches.add(b.id())));cursor=page.nextCursor();}while(cursor!=null);assertEquals(16,branches.size());
  failure(400,()->base.sessionPage(1,101,null));failure(400,()->base.branchPage(1,session.id(),5,"malformed"));failure(404,()->base.branchPage(2,session.id(),5,null));
 }
 @Test void completeHistoryAndTimelineBrowseBeyondOldLimitsWithStableBranchSnapshot(){
  var session=session(1);var ids=seedMessages(session,1023);var page=base.historyPage(1,session.id(),session.activeBranchId(),false,100,null);var seen=new ArrayList<>(page.items().stream().map(AssistantRecords.Message::id).toList());
  // A later answer/new user input must not change the snapshot of an already started browse.
  UUID later=UUID.randomUUID();jdbc.update("INSERT INTO assistant_messages(id,session_id,parent_message_id,version_group_id,role,body,state,created_at) VALUES(?,?,?,?,'USER','later','ACCEPTED',?)",later,session.id(),ids.get(ids.size()-1),later,Timestamp.from(epoch.plusSeconds(3)));jdbc.update("UPDATE assistant_branches SET head_message_id=? WHERE id=?",later,session.activeBranchId());
  String cursor=page.nextCursor();while(cursor!=null){page=base.historyPage(1,session.id(),session.activeBranchId(),false,100,cursor);seen.addAll(0,page.items().stream().map(AssistantRecords.Message::id).toList());cursor=page.nextCursor();}assertEquals(ids,seen);
  int timeline=0;cursor=null;do{var rows=execution.timelinePage(1,session.id(),session.activeBranchId(),false,100,cursor);timeline+=rows.items().size();assertTrue(rows.items().stream().allMatch(row->row.classificationVersion()==0));cursor=rows.nextCursor();}while(cursor!=null);assertEquals(1024,timeline);
  int all=0;cursor=null;do{var rows=base.historyPage(1,session.id(),null,true,100,cursor);all+=rows.items().size();cursor=rows.nextCursor();}while(cursor!=null);assertEquals(1024,all);
 }
 @Test void runAndSummaryPagesAreBoundedCompleteAndDoNotDispatch(){
  var session=session(1);var messages=seedMessages(session,1);UUID active=null;
  for(int i=0;i<123;i++){UUID run=UUID.randomUUID();jdbc.update("INSERT INTO assistant_runs(id,session_id,branch_id,input_message_id,kind,status,context_version,created_at,updated_at) VALUES(?,?,?,?,'SUMMARY','COMPLETED',0,?,?)",run,session.id(),session.activeBranchId(),messages.get(0),Timestamp.from(epoch.plusSeconds(i)),Timestamp.from(epoch.plusSeconds(i)));
   jdbc.update("INSERT INTO assistant_summaries(id,session_id,branch_id,head_message_id,context_hash,model_config_hash,prompt_version,current_run_id,body) VALUES(?,?,?,?,?,?,?,?,'summary')",UUID.randomUUID(),session.id(),session.activeBranchId(),messages.get(0),PostgresAssistantStore.digest("context-"+i),"a".repeat(64),"fixture-v1",run);}
  active=UUID.randomUUID();jdbc.update("INSERT INTO assistant_runs(id,session_id,branch_id,input_message_id,kind,status,context_version,created_at,updated_at) VALUES(?,?,?,?,'CHAT','QUEUED',0,?,?)",active,session.id(),session.activeBranchId(),messages.get(0),Timestamp.from(epoch.plusSeconds(124)),Timestamp.from(epoch.plusSeconds(124)));
  assertEquals(List.of(active),execution.runPage(1,session.id(),null,true,50,null).items().stream().map(AssistantExecutionRecords.Run::id).toList());
  int runs=0,summaries=0;String cursor=null;do{var page=execution.runPage(1,session.id(),null,false,17,cursor);runs+=page.items().size();cursor=page.nextCursor();}while(cursor!=null);assertEquals(124,runs);
  cursor=null;do{var page=execution.summaryPage(1,session.id(),null,19,cursor);summaries+=page.items().size();cursor=page.nextCursor();}while(cursor!=null);assertEquals(123,summaries);
  assertEquals("QUEUED",execution.get(1,active).status());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assistant_runs WHERE dispatched_at IS NOT NULL",Integer.class));
  failure(404,()->execution.runPage(2,session.id(),null,true,50,null));failure(404,()->execution.summaryPage(2,session.id(),null,50,null));
 }
 @Test void pagesRecheckLifecycleAndRejectCrossSessionOrCrossBranchCursor(){
  var a=session(1);var b=session(1);seedMessages(a,7);seedMessages(b,7);var page=base.historyPage(1,a.id(),a.activeBranchId(),false,2,null);
  failure(400,()->base.historyPage(1,b.id(),b.activeBranchId(),false,2,page.nextCursor()));failure(404,()->base.historyPage(2,a.id(),a.activeBranchId(),false,2,page.nextCursor()));failure(404,()->base.historyPage(1,a.id(),b.activeBranchId(),false,2,null));
  var timeline=execution.timelinePage(1,a.id(),null,false,2,null);assertTrue(timeline.hasMore());
  execution.classify(1,a.id(),timeline.items().get(0).messageId(),"classification-one",0,"构图规划");assertEquals(1,execution.timelinePage(1,a.id(),null,false,2,null).items().get(0).classificationVersion());
  base.deleteSession(1,a.id());failure(404,()->base.historyPage(1,a.id(),a.activeBranchId(),false,2,page.nextCursor()));failure(404,()->execution.timelinePage(1,a.id(),null,false,2,timeline.nextCursor()));failure(404,()->execution.runPage(1,a.id(),null,true,20,null));
 }
 @Test void requestResolutionIsReadOnlyOwnerBoundAndNeverReleasesExpiredOrDeletedKeys(){
  var a=base.createSession(1,"lost-create-response","synthetic");var found=base.resolveRequest(1,"CREATE_SESSION","lost-create-response",null);assertEquals(a.id(),found.resultId());assertEquals("SESSION",found.resultType());assertEquals("FOUND",found.status());
  assertEquals("NOT_FOUND",base.resolveRequest(2,"CREATE_SESSION","lost-create-response",null).status());
  failure(404,()->base.resolveRequest(2,"CREATE_SESSION","lost-create-response",a.id()));
  var command=new AssistantExecutionRecords.Admission(a.id(),a.activeBranchId(),0,"lost-chat-response","saved input",List.of(),"mock-text",Map.of());var run=execution.admit(1,command).run();
  assertEquals(run.id(),base.resolveRequest(1,"CHAT_EXECUTION","lost-chat-response",a.id()).resultId());assertEquals("QUEUED",execution.get(1,run.id()).status());
  assertEquals("NOT_FOUND",base.resolveRequest(1,"CHAT_EXECUTION","never-sent-key",a.id()).status());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assistant_runs",Integer.class));
  failure(409,()->execution.admit(1,new AssistantExecutionRecords.Admission(a.id(),a.activeBranchId(),0,"lost-chat-response","changed",List.of(),"mock-text",Map.of())));
  jdbc.update("UPDATE assistant_requests SET expires_at=created_at-interval '1 second',created_at=created_at-interval '2 seconds' WHERE operation='CHAT_EXECUTION'");
  failure(410,()->base.resolveRequest(1,"CHAT_EXECUTION","lost-chat-response",a.id()));assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM assistant_requests WHERE operation='CHAT_EXECUTION'",String.class));
  base.deleteSession(1,a.id());failure(410,()->base.resolveRequest(1,"CREATE_SESSION","lost-create-response",null));failure(410,()->base.resolveRequest(1,"CHAT_EXECUTION","lost-chat-response",a.id()));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assistant_runs",Integer.class));
 }

 @Test void branchPaginationReachesBeforeFormerTenThousandDepthLimit(){
  var session=session(1);var expected=seedMessages(session,10007);var seen=new ArrayList<UUID>();String cursor=null;
  do{var page=base.historyPage(1,session.id(),session.activeBranchId(),false,100,cursor);seen.addAll(0,page.items().stream().map(AssistantRecords.Message::id).toList());cursor=page.nextCursor();}while(cursor!=null);
  assertEquals(expected,seen);assertEquals(10007,new HashSet<>(seen).size());
 }

}

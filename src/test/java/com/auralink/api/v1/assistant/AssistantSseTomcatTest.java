package com.auralink.api.v1.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.auralink.assistant.*;
import com.auralink.assistant.AssistantExecutionRecords.*;
import com.auralink.entity.User;
import com.auralink.security.jwt.JwtTokenProvider;
import com.auralink.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Real loopback Tomcat/HTTP transport; persistence and user resolution are fixtures. */
class AssistantSseTomcatTest {
 @TempDir Path temporary;
 final ObjectMapper json=new ObjectMapper();
 final AssistantExecutionStore store=mock(AssistantExecutionStore.class);
 final AtomicInteger guardedWrites=new AtomicInteger();
 final AtomicBoolean deleted=new AtomicBoolean();
 final UUID runId=UUID.randomUUID(),branchId=UUID.randomUUID();
 final String answer="画".repeat(60_000);
 AssistantExecutionController controller;
 Run run;
 Tomcat tomcat;
 URI endpoint;
 @BeforeEach void start() throws Exception {
  var users=mock(CurrentUserService.class);User user=new User();user.setId(17L);when(users.requireCurrentUser()).thenReturn(user);
  var runtime=mock(AssistantExecutionRuntime.class);var service=mock(AssistantExecutionService.class);
  when(runtime.require()).thenReturn(service);when(service.store()).thenReturn(store);
  var properties=new AssistantExecutionProperties();properties.setSubscriptionTimeout(Duration.ofSeconds(10));when(service.properties()).thenReturn(properties);
  var tokens=mock(JwtTokenProvider.class);when(tokens.extractExpiration("synthetic-test-session")).thenReturn(new Date(System.currentTimeMillis()+60_000));
  run=mock(Run.class);when(run.sequence()).thenReturn(29L);when(run.branchId()).thenReturn(branchId);
  when(run.status()).thenReturn("COMPLETED");when(run.terminal()).thenReturn(true);when(run.checkpoint()).thenReturn(answer);
  when(store.events(17,runId,0,1)).thenReturn(new EventPage(run,true,List.of()));
  doAnswer(call->{synchronized(deleted){
   if(deleted.get())throw AssistantFailure.notFound();
   guardedWrites.incrementAndGet();
   long after=call.getArgument(2);Function<EventPage,Object> consume=call.getArgument(4);
   return consume.apply(new EventPage(run,after<29,List.of()));
  }}).when(store).withEvents(eq(17L),eq(runId),anyLong(),eq(1),any());
  controller=new AssistantExecutionController(users,runtime,json,tokens);
  tomcat=new Tomcat();tomcat.setBaseDir(temporary.toString());tomcat.setPort(0);
  tomcat.getConnector().setProperty("address","127.0.0.1");
  var context=tomcat.addContext("",temporary.toString());
  var wrapper=Tomcat.addServlet(context,"events",new HttpServlet(){
   @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{
    controller.events(runId,0,request,response);
   }
  });wrapper.setAsyncSupported(true);context.addServletMappingDecoded("/events","events");tomcat.start();
  endpoint=URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/events");
 }
 @AfterEach void stop()throws Exception {if(controller!=null)controller.closeSubscriptions();if(tomcat!=null){tomcat.stop();tomcat.destroy();}}
 @Test void largeChineseSnapshotHasCompleteUtf8AndNoLeakedHttpChunkFraming()throws Exception{
  // Repetition exercises callback and timer scheduling, not just mock ServletOutputStream writes.
  for(int attempt=0;attempt<3;attempt++){
   var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer synthetic-test-session").GET().build();
   var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(request,HttpResponse.BodyHandlers.ofByteArray());
   assertThat(response.statusCode()).isEqualTo(200);
   String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();
   assertThat(body).startsWith("id: 29\nevent: message.snapshot\ndata: ").endsWith("\n\n");
   var payload=json.readTree(body.substring(body.indexOf("data: ")+6).trim());
   assertThat(payload.get("text").asText()).isEqualTo(answer);assertThat(payload.get("sequence").asLong()).isEqualTo(29);
  }
  assertThat(guardedWrites.get()).isGreaterThanOrEqualTo(3*((answer.getBytes(StandardCharsets.UTF_8).length+8191)/8192));
 }
 @Test void maximumControlCharacterCheckpointEscapesAndRecoversCompletely()throws Exception{
  String escapedAnswer="\u0001".repeat(128_000);when(run.checkpoint()).thenReturn(escapedAnswer);
  var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer synthetic-test-session").GET().build();
  var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());
  assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body().length).isGreaterThan(768_000);
  String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();
  assertThat(body).endsWith("\n\n");
  var payload=json.readTree(body.substring(body.indexOf("data: ")+6).trim());
  assertThat(payload.get("text").asText()).isEqualTo(escapedAnswer);
  assertThat(payload.get("sequence").asLong()).isEqualTo(29);
 }
 @Test void shortExecutingEventArrivesBeforeTerminalOrSubscriptionTimeout()throws Exception{
  when(run.sequence()).thenReturn(1L);when(run.status()).thenReturn("EXECUTING");when(run.terminal()).thenReturn(false);when(run.checkpoint()).thenReturn("已保存的中文片段");
  doAnswer(call->{synchronized(deleted){
   if(deleted.get())throw AssistantFailure.notFound();guardedWrites.incrementAndGet();long after=call.getArgument(2);Function<EventPage,Object> consume=call.getArgument(4);
   var event=new Event(1,"message.delta",Map.of("contractVersion",1,"runId",runId.toString(),"branchId",branchId.toString(),"sequence",1,"text","已保存的中文片段"),java.time.Instant.now());
   return consume.apply(new EventPage(run,false,after<1?List.of(event):List.of()));
  }}).when(store).withEvents(eq(17L),eq(runId),anyLong(),eq(1),any());
  var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer synthetic-test-session").GET().build();
  long started=System.nanoTime();
  var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build().send(request,HttpResponse.BodyHandlers.ofInputStream());
  assertThat(response.statusCode()).isEqualTo(200);
  try(var input=response.body()){
   var read=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{var bytes=new java.io.ByteArrayOutputStream();int last=-1;for(int b;(b=input.read())>=0;){bytes.write(b);if(last=='\n'&&b=='\n')break;last=b;}return bytes.toString(StandardCharsets.UTF_8);}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}});
   String frame=read.get(2,java.util.concurrent.TimeUnit.SECONDS);assertThat(frame).contains("event: message.delta").contains("已保存的中文片段").endsWith("\n\n");
   assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(3));assertThat(run.terminal()).isFalse();
  }
 }
 @Test void deletedSessionRevokesCachedTailBeforeAnotherChunk()throws Exception{
  var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer synthetic-test-session").GET().build();
  var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofInputStream());
  try(var input=response.body()){
   byte[] first=input.readNBytes(8192);assertThat(first).hasSize(8192);
   int writtenAtDeletion; synchronized(deleted){deleted.set(true);writtenAtDeletion=guardedWrites.get();}
   byte[] tail=input.readAllBytes();
   assertThat(guardedWrites.get()).isEqualTo(writtenAtDeletion);
   assertThat(first.length+tail.length).isLessThan(answer.getBytes(StandardCharsets.UTF_8).length);
  }
 }
}

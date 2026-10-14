package com.auralink.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssistantTransportTest {
 @TempDir Path root;
 HttpServer server;
 ExecutorService executor;
 @AfterEach void stop(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();}
 AssistantAiClient client()throws Exception{return client(Duration.ofMillis(300));}
 AssistantAiClient client(Duration retrievalTimeout)throws Exception{
  if(server==null){server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.start();}
  Path token=root.resolve("token");Files.writeString(token,"synthetic-private-token-for-local-tests");Files.setPosixFilePermissions(token,PosixFilePermissions.fromString("rw-------"));
  var p=new AssistantExecutionProperties();p.setEnabled(true);p.setAllowLoopbackHttp(true);p.setAiBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());p.setInternalTokenFile(token.toString());p.setExpectedConfigSha256("a".repeat(64));p.setIdleTimeout(Duration.ofMillis(200));p.setTotalTimeout(Duration.ofMillis(600));p.setRetrievalTimeout(retrievalTimeout);
  return new AssistantAiClient(p,new ObjectMapper());
 }
 @Test void closedConnectionDoesNotRetryPotentiallyChargedPost()throws Exception{
  try(var client=client()){
   var count=new AtomicInteger();server.createContext("/internal/v1/assistant/executions",exchange->{count.incrementAndGet();exchange.getRequestBody().readAllBytes();exchange.close();});
   assertThrows(IOException.class,()->client.execute(UUID.randomUUID(),Map.of("fixture",true),(e,d)->{}));
   assertEquals(1,count.get());
  }
 }
 @Test void actualSplitUtf8SseAndExplicitTerminal()throws Exception{
  try(var client=client()){
   server.createContext("/internal/v1/assistant/executions",exchange->{exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){byte[] bytes="event: delta\r\ndata: {\"text\":\"山水\"}\r\n\r\nevent: done\ndata: {\"finishReason\":\"stop\"}\n\n".getBytes(StandardCharsets.UTF_8);for(byte b:bytes){out.write(b);out.flush();}}});
   var events=new ArrayList<String>();client.execute(UUID.randomUUID(),Map.of(),(type,data)->events.add(type+":"+data.get("text")));
   assertEquals(List.of("delta:山水","done:null"),events);
  }
 }
 @Test void timeoutAndCancellationDoNotWaitForWholeBody()throws Exception{
  try(var client=client()){
   CountDownLatch headers=new CountDownLatch(1);server.createContext("/internal/v1/assistant/executions",exchange->{exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){out.write(": waiting\n\n".getBytes(StandardCharsets.UTF_8));out.flush();headers.countDown();Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   UUID id=UUID.randomUUID();Future<?> active=executor.submit(()->{assertThrows(IOException.class,()->client.execute(id,Map.of(),(e,d)->{}));});
   assertTrue(headers.await(1,TimeUnit.SECONDS));long start=System.nanoTime();client.closeStream(id);active.get(1,TimeUnit.SECONDS);assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));
  }
 }
 @Test void codecRejectsMissingTerminalOversizeAndBrokenUtf8()throws Exception{
  assertThrows(EOFException.class,()->AssistantSseReader.read(new ByteArrayInputStream("event: delta\ndata: x\n\n".getBytes()),100,100,(e,d)->{}));
  assertThrows(IOException.class,()->AssistantSseReader.read(new ByteArrayInputStream("data: abcdefghijkl\n\n".getBytes()),8,100,(e,d)->{}));
  assertThrows(IOException.class,()->AssistantSseReader.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}),100,100,(e,d)->{}));
 }
 @Test void retrievalDeadlineIsDistinctAndNeverRetries()throws Exception{
  try(var client=client(Duration.ofMillis(150))){
   var count=new AtomicInteger();server.createContext("/internal/v1/assistant/retrieval",exchange->{count.incrementAndGet();exchange.getRequestBody().readAllBytes();try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}exchange.close();});
   long started=System.nanoTime();var failure=assertThrows(AssistantFailure.class,()->client.retrieval("synthetic",true));
   assertEquals("ASSISTANT_RETRIEVAL_TIMEOUT",failure.code());assertEquals(1,count.get());
   assertTrue(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(1));
  }
 }
 @Test void retrievalUsesOwnConfiguredWindowAndPreservesAiTimeoutState()throws Exception{
  assertEquals(Duration.ofSeconds(31),new AssistantExecutionProperties().getRetrievalTimeout());
  try(var client=client(Duration.ofMillis(1000))){
   server.createContext("/internal/v1/assistant/retrieval",exchange->{exchange.getRequestBody().readAllBytes();try{Thread.sleep(350);}catch(InterruptedException e){Thread.currentThread().interrupt();}byte[] value="{\"state\":\"TIMEOUT\",\"citations\":[]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,value.length);try(var out=exchange.getResponseBody()){out.write(value);}});
   assertEquals("TIMEOUT",client.retrieval("synthetic",true).get("state"));
  }
 }
 @Test void derivativeValidatesActualFormatAndPreservesOriginalHash()throws Exception{
  var image=new BufferedImage(16,8,BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);byte[] bytes=out.toByteArray();
  var derivative=AssistantImages.prepare(bytes,"image/png","fixture");assertEquals(16,derivative.manifest().get("width"));assertEquals(AssistantAttachmentStorage.sha256(bytes),derivative.manifest().get("originalSha256"));
  assertThrows(AssistantFailure.class,()->AssistantImages.prepare(bytes,"image/jpeg","fixture"));
  assertThrows(AssistantFailure.class,()->AssistantImages.prepare(new byte[]{1,2,3},"image/png","fixture"));
 }
}

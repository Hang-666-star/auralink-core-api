package com.auralink.api.v1.assistant;

import com.auralink.api.v1.error.ApiV1ErrorResponse;
import com.auralink.assistant.*;
import com.auralink.security.jwt.JwtTokenProvider;
import com.auralink.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import jakarta.servlet.*;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantExecutionController {
 private final CurrentUserService users;private final AssistantExecutionRuntime runtime;private final ObjectMapper json;private final JwtTokenProvider tokens;
 private final Semaphore subscriptions=new Semaphore(32);
 private final ScheduledExecutorService subscriptionPolls=Executors.newScheduledThreadPool(2,r->{Thread t=new Thread(r,"assistant-subscription");t.setDaemon(true);return t;});
 private final ConcurrentHashMap<AsyncContext,Runnable> activeSubscriptions=new ConcurrentHashMap<>();
 public AssistantExecutionController(CurrentUserService users,AssistantExecutionRuntime runtime,ObjectMapper json,JwtTokenProvider tokens){this.users=users;this.runtime=runtime;this.json=json;this.tokens=tokens;}
 private long owner(){return users.requireCurrentUser().getId();}
 private AssistantExecutionService service(){return runtime.require();}
 private static <T>ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping("/models") public ResponseEntity<?> models(){owner();return ok(service().models());}
 @GetMapping("/artworks") public ResponseEntity<?> artworks(@RequestParam String query){long owner=owner();return ok(service().search(owner,query));}
 @PostMapping("/sessions/{id}/runs") public ResponseEntity<?> submit(@PathVariable UUID id,@Valid @RequestBody AssistantExecutionDtos.Chat body){long owner=owner();return ok(AssistantExecutionDtos.accepted(service().admit(owner,id,body.command())));}
 @GetMapping("/runs/{id}") public ResponseEntity<?> run(@PathVariable UUID id){long owner=owner();return ok(AssistantExecutionDtos.run(service().store().get(owner,id)));}
 @PostMapping("/runs/{id}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID id){long owner=owner();return ok(AssistantExecutionDtos.run(service().cancel(owner,id)));}
 @PostMapping("/runs/{id}/retry") public ResponseEntity<?> retry(@PathVariable UUID id,@Valid @RequestBody AssistantExecutionDtos.Retry body){long owner=owner();return ok(AssistantExecutionDtos.accepted(service().retry(owner,id,body.requestKey(),body.expectedVersion(),body.acknowledgePossiblePriorCharge())));}
 @GetMapping("/runs/{id}/citations") public ResponseEntity<?> citations(@PathVariable UUID id){long owner=owner();return ok(service().store().citations(owner,id));}
 @GetMapping("/runs/{id}/choices") public ResponseEntity<?> choices(@PathVariable UUID id){long owner=owner();return ok(service().store().choices(owner,id));}
 @PostMapping("/runs/{id}/choices") public ResponseEntity<?> select(@PathVariable UUID id,@Valid @RequestBody AssistantExecutionDtos.Choice body){long owner=owner();return ok(AssistantExecutionDtos.accepted(service().choice(owner,id,body.cardId(),body.optionId(),body.command())));}
 @PostMapping("/sessions/{id}/messages/{message}/revisions") public ResponseEntity<?> revise(@PathVariable UUID id,@PathVariable UUID message,@Valid @RequestBody AssistantExecutionDtos.Revision body){long owner=owner();return ok(AssistantDtos.branch(service().revise(owner,id,message,body.branchId(),body.expectedVersion(),body.requestKey(),body.text())));}
 @PostMapping("/sessions/{id}/edited-runs") public ResponseEntity<?> edited(@PathVariable UUID id,@Valid @RequestBody AssistantExecutionDtos.Chat body){long owner=owner();return ok(AssistantExecutionDtos.accepted(service().executeEdited(owner,id,body.command())));}
 @PostMapping("/sessions/{id}/retrievals") public ResponseEntity<?> retrieval(@PathVariable UUID id,@Valid @RequestBody AssistantExecutionDtos.Chat body){long owner=owner();return ok(AssistantExecutionDtos.accepted(service().retrieval(owner,id,body.command())));}
 @PostMapping("/sessions/{id}/summaries") public ResponseEntity<?> summary(@PathVariable UUID id,@Valid @RequestBody AssistantExecutionDtos.Summary body){long owner=owner();return ok(AssistantExecutionDtos.run(service().summary(owner,id,body.branchId(),body.requestKey())));}
 @GetMapping("/sessions/{id}/summaries") public ResponseEntity<?> summaries(@PathVariable UUID id,@RequestParam(required=false)UUID branchId){long owner=owner();return ok(service().store().summaries(owner,id,branchId));}
 @GetMapping("/sessions/{id}/timeline") public ResponseEntity<?> timeline(@PathVariable UUID id,@RequestParam(required=false)UUID branchId,@RequestParam(defaultValue="false")boolean includeHistory){long owner=owner();return ok(service().store().timeline(owner,id,branchId,includeHistory).stream().map(AssistantExecutionDtos::timeline).toList());}
 @GetMapping("/sessions/{id}/runs/page") public ResponseEntity<?> runPage(@PathVariable UUID id,@RequestParam(required=false)UUID branchId,@RequestParam(defaultValue="false")boolean activeOnly,@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String cursor){long owner=owner();return ok(service().store().runPage(owner,id,branchId,activeOnly,limit,cursor).map(AssistantExecutionDtos::run));}
 @GetMapping("/sessions/{id}/summaries/page") public ResponseEntity<?> summaryPage(@PathVariable UUID id,@RequestParam(required=false)UUID branchId,@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String cursor){long owner=owner();return ok(service().store().summaryPage(owner,id,branchId,limit,cursor));}
 @GetMapping("/sessions/{id}/timeline/page") public ResponseEntity<?> timelinePage(@PathVariable UUID id,@RequestParam(required=false)UUID branchId,@RequestParam(defaultValue="false")boolean includeHistory,@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String cursor){long owner=owner();return ok(service().store().timelinePage(owner,id,branchId,includeHistory,limit,cursor).map(AssistantExecutionDtos::timeline));}
 @PostMapping("/sessions/{id}/messages/{message}/classification") public ResponseEntity<?> classify(@PathVariable UUID id,@PathVariable UUID message,@Valid @RequestBody AssistantExecutionDtos.Classification body){long owner=owner();return ok(service().store().classify(owner,id,message,body.requestKey(),body.expectedVersion(),body.label()));}

 /** Nonblocking, bounded subscription. Reads/reconnects never dispatch execution. */
 @GetMapping("/runs/{id}/events")
 public void events(@PathVariable UUID id,@RequestParam(defaultValue="0")long after,HttpServletRequest request,HttpServletResponse response)throws IOException{
  long owner=owner();var service=service();var store=service.store();
  String last=request.getHeader("Last-Event-ID");if(last!=null&&!last.isBlank()){try{long cursor=Long.parseLong(last);if(after!=0&&after!=cursor)throw AssistantFailure.invalid("Conflicting event cursors");after=cursor;}catch(NumberFormatException invalid){throw AssistantFailure.invalid("Invalid event cursor");}}
  store.events(owner,id,after,1); // Pre-stream failures remain authenticated JSON errors.
  String authorization=request.getHeader("Authorization");
  if(authorization==null||!authorization.startsWith("Bearer "))throw new AssistantFailure(401,"UNAUTHORIZED","A bearer session is required");
  long expiry=tokens.extractExpiration(authorization.substring(7)).getTime();
  if(!request.isAsyncSupported())throw new AssistantFailure(503,"ASSISTANT_STREAM_UNAVAILABLE","This listener does not support bounded asynchronous streaming");
  if(!subscriptions.tryAcquire())throw new AssistantFailure(429,"ASSISTANT_STREAM_LIMIT","Too many Assistant subscriptions");
  if(32-subscriptions.availablePermits()>service.properties().getMaxSubscriptions()){subscriptions.release();throw new AssistantFailure(429,"ASSISTANT_STREAM_LIMIT","Too many Assistant subscriptions");}
  AsyncContext async;
  try{async=request.startAsync();}catch(RuntimeException failure){subscriptions.release();throw new AssistantFailure(503,"ASSISTANT_STREAM_UNAVAILABLE","Asynchronous streaming is unavailable");}
  response.setStatus(200);response.setContentType("text/event-stream;charset=UTF-8");response.setHeader("Cache-Control","no-store");response.setHeader("X-Accel-Buffering","no");response.setHeader("X-Content-Type-Options","nosniff");
  long duration=service.properties().getSubscriptionTimeout().toMillis();long deadline=System.currentTimeMillis()+duration;
  AtomicLong cursor=new AtomicLong(after);AtomicInteger bytes=new AtomicInteger();AtomicBoolean closed=new AtomicBoolean();AtomicBoolean writing=new AtomicBoolean();AtomicReference<ScheduledFuture<?>> timer=new AtomicReference<>();
  AtomicReference<PendingFrame> pending=new AtomicReference<>();AtomicBoolean drainedTerminal=new AtomicBoolean();
  Runnable release=()->{if(closed.compareAndSet(false,true)){var future=timer.get();if(future!=null)future.cancel(false);activeSubscriptions.remove(async);subscriptions.release();}};
  Runnable finish=()->{release.run();try{async.complete();}catch(IllegalStateException ignored){/* Already completed by disconnect or container timeout. */}};
  activeSubscriptions.put(async,finish);
  async.addListener(new AsyncListener(){
   public void onComplete(AsyncEvent event){release.run();}
   public void onTimeout(AsyncEvent event){finish.run();}
   public void onError(AsyncEvent event){finish.run();}
   public void onStartAsync(AsyncEvent event){finish.run();}
  });
  async.setTimeout(duration);
  ServletOutputStream output=response.getOutputStream();
  Runnable poll=()->{
   if(closed.get()||!writing.compareAndSet(false,true))return;
   try{
    if(System.currentTimeMillis()>=deadline||System.currentTimeMillis()>=expiry){pending.set(null);finish.run();return;}
    if(!output.isReady())return;
    // A nonblocking write may still be pending inside Tomcat after write() returns.
    // Never complete in the same invocation that submitted the final bytes.
    if(drainedTerminal.get()){finish.run();return;}
    store.withEvents(owner,id,cursor.get(),1,page->{
     if(closed.get()||System.currentTimeMillis()>=expiry||!output.isReady())return null;
     try{
      PendingFrame current=pending.get();
      if(current==null){
       long sequence;String event;Object value;
       if(page.snapshotRequired()){
        sequence=page.run().sequence();event="message.snapshot";
        value=Map.of("contractVersion",1,"runId",id.toString(),"branchId",page.run().branchId().toString(),"sequence",sequence,"state",page.run().status(),"text",page.run().checkpoint());
       }else if(!page.events().isEmpty()){
        var next=page.events().get(0);sequence=next.sequence();event=next.type();value=next.payload();
       }else{if(page.run().terminal()&&cursor.get()>=page.run().sequence())finish.run();return null;}
       byte[] frame=frame(sequence,event,value);
       // Stop between complete SSE events. A partial frame must not consume the
       // connection budget and then be mistaken for a complete checkpoint.
       if(bytes.get()+frame.length>2097152){finish.run();return null;}
       current=new PendingFrame(frame,sequence);pending.set(current);
      }
      // The session lifecycle lock is held for EVERY ready 8-KiB write, including
      // cached frame tails. A committed tombstone revokes the remaining bytes.
      int count=Math.min(8192,current.data.length-current.offset);
      output.write(current.data,current.offset,count);
      current.offset+=count;bytes.addAndGet(count);
      if(current.offset==current.data.length){
       // A complete small event must reach the client while the run is still
       // executing. Tomcat otherwise buffers it until 8 KiB or async completion.
       // Flush through the nonblocking stream, inside the same lifecycle guard;
       // keep terminal async completion on the later ready callback below.
       output.flush();
       cursor.set(current.sequence);pending.set(null);
       if(page.run().terminal()&&cursor.get()>=page.run().sequence()||bytes.get()>=2097152)drainedTerminal.set(true);
      }
     }catch(IOException|IllegalStateException failed){pending.set(null);finish.run();}
     return null;
    });
   }catch(AssistantFailure|DataAccessException|IllegalStateException revoked){pending.set(null);finish.run();}
   finally{writing.set(false);}
  };
  output.setWriteListener(new WriteListener(){
   // Execute on the container callback itself; returning and writing later on an
   // unrelated scheduler can race the container's socket flush/chunk framing.
   public void onWritePossible(){poll.run();}
   public void onError(Throwable failure){pending.set(null);finish.run();}
  });
  Runnable wake=()->{if(!closed.get())try{async.start(poll);}catch(IllegalStateException stopped){finish.run();}};
  var future=subscriptionPolls.scheduleWithFixedDelay(wake,50,50,TimeUnit.MILLISECONDS);timer.set(future);if(closed.get())future.cancel(false);
 }
 private static final class PendingFrame {final byte[] data;final long sequence;int offset;PendingFrame(byte[] data,long sequence){this.data=data;this.sequence=sequence;}}
 private byte[] frame(long sequence,String event,Object payload)throws IOException{byte[] bytes=("id: "+sequence+"\nevent: "+event+"\ndata: "+json.writeValueAsString(payload)+"\n\n").getBytes(StandardCharsets.UTF_8);if(bytes.length>1048576)throw new IOException("Assistant event exceeds output limit");return bytes;}
 @PreDestroy public void closeSubscriptions(){activeSubscriptions.values().forEach(Runnable::run);subscriptionPolls.shutdownNow();}
 @ExceptionHandler(AssistantFailure.class) public ResponseEntity<ApiV1ErrorResponse> failure(AssistantFailure failure,HttpServletRequest request){return error(failure.status(),failure.code(),failure.getMessage(),request);}
 @ExceptionHandler(DataAccessException.class) public ResponseEntity<ApiV1ErrorResponse> unavailable(DataAccessException failure,HttpServletRequest request){return error(503,"ASSISTANT_STORAGE_UNAVAILABLE","Assistant storage is temporarily unavailable",request);}
 private ResponseEntity<ApiV1ErrorResponse> error(int status,String code,String message,HttpServletRequest request){String correlation=UUID.randomUUID().toString();return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).header("X-Correlation-ID",correlation).body(new ApiV1ErrorResponse(Instant.now(),status,code,message,request.getRequestURI(),correlation,Map.of(),List.of()));}
}

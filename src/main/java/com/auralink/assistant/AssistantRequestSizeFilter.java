package com.auralink.assistant;

import com.auralink.api.v1.error.ApiV1ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assistant JSON is bounded before Jackson materializes lists/unknown fields.
 * Runs after the existing security chain; multipart remains under the existing
 * upload contract, and all unrelated API routes remain untouched. */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public final class AssistantRequestSizeFilter extends OncePerRequestFilter {
 public static final int MAX_JSON_BYTES=256*1024;
 private final ObjectMapper json;
 public AssistantRequestSizeFilter(ObjectMapper json){this.json=json;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){
  String uri=request.getRequestURI().substring(request.getContextPath().length());
  String type=Objects.toString(request.getContentType(),"").split(";",2)[0].strip().toLowerCase(Locale.ROOT);
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  return !(uri.equals("/api/v1/assistant")||uri.startsWith("/api/v1/assistant/"))
   || !(type.equals("application/json")||type.startsWith("application/")&&type.endsWith("+json"))
   || authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken;
 }
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
  if(request.getContentLengthLong()>MAX_JSON_BYTES){tooLarge(request,response);return;}
  // Also bounds chunked/misdeclared requests. Never buffer the whole untrusted
  // request and only then check its length; read at most limit + one byte.
  byte[] body=request.getInputStream().readNBytes(MAX_JSON_BYTES+1);
  if(body.length>MAX_JSON_BYTES){tooLarge(request,response);return;}
  chain.doFilter(new Body(request,body),response);
 }
 private void tooLarge(HttpServletRequest request,HttpServletResponse response)throws IOException{
  String correlation=UUID.randomUUID().toString();response.setStatus(413);response.setContentType("application/json");response.setCharacterEncoding(StandardCharsets.UTF_8.name());response.setHeader("Cache-Control","no-store");response.setHeader("X-Correlation-ID",correlation);
  json.writeValue(response.getOutputStream(),new ApiV1ErrorResponse(Instant.now(),413,"ASSISTANT_REQUEST_TOO_LARGE","Assistant JSON request exceeds 256 KiB",request.getRequestURI(),correlation,Map.of(),List.of()));
 }
 private static final class Body extends HttpServletRequestWrapper {
  private final byte[] body;
  private final ServletInputStream stream;
  Body(HttpServletRequest original,byte[] bytes){super(original);body=bytes;var input=new ByteArrayInputStream(bytes);stream=new ServletInputStream(){
   public int read(){return input.read();}
   public int read(byte[] target,int offset,int length){return input.read(target,offset,length);}
   public boolean isFinished(){return input.available()==0;}
   public boolean isReady(){return true;}
   public void setReadListener(ReadListener listener){throw new IllegalStateException("Bounded JSON parsing uses synchronous request reads");}
  };}
  @Override public ServletInputStream getInputStream(){return stream;}
  @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));}
  @Override public int getContentLength(){return body.length;}
  @Override public long getContentLengthLong(){return body.length;}
 }
}

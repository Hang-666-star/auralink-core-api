package com.auralink.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class AssistantRequestSizeFilterTest {
 private final AssistantRequestSizeFilter filter=new AssistantRequestSizeFilter(new ObjectMapper().findAndRegisterModules());
 @BeforeEach void authenticate(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("synthetic-owner",null,List.of()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private MockHttpServletRequest request(String path,String type,byte[] body){var request=new MockHttpServletRequest("POST",path);request.setContentType(type);request.setContent(body);return request;}
 @Test void declaredOversizeRejectedBeforeControllerAndDoesNotEchoBody()throws Exception{
  var req=request("/api/v1/assistant/sessions/id/runs","application/json",("private-must-not-echo"+"x".repeat(AssistantRequestSizeFilter.MAX_JSON_BYTES)).getBytes(StandardCharsets.UTF_8));var response=new MockHttpServletResponse();var reached=new AtomicBoolean();
  filter.doFilter(req,response,(r,s)->reached.set(true));assertEquals(413,response.getStatus());assertFalse(reached.get());assertFalse(response.getContentAsString().contains("private-must-not-echo"));assertTrue(response.getContentAsString().contains("ASSISTANT_REQUEST_TOO_LARGE"));
 }
 @Test void chunkedOversizeRejectedAndExactBoundIsReadable()throws Exception{
  var chunked=new MockHttpServletRequest("POST","/api/v1/assistant/sessions/id/runs"){@Override public long getContentLengthLong(){return -1;}@Override public int getContentLength(){return -1;}};
  chunked.setContentType("application/json");chunked.setContent(new byte[AssistantRequestSizeFilter.MAX_JSON_BYTES+1]);var response=new MockHttpServletResponse();
  filter.doFilter(chunked,response,(r,s)->fail("Chunked oversized body reached controller"));assertEquals(413,response.getStatus());
  byte[] exact=new byte[AssistantRequestSizeFilter.MAX_JSON_BYTES];Arrays.fill(exact,(byte)' ');var valid=request("/api/v1/assistant/sessions","application/json;charset=UTF-8",exact);
  filter.doFilter(valid,new MockHttpServletResponse(),(r,s)->assertArrayEquals(exact,r.getInputStream().readAllBytes()));
 }
 @Test void multipartOtherRoutesAndUnauthenticatedRequestsRemainUnderExistingContracts()throws Exception{
  byte[] huge=new byte[AssistantRequestSizeFilter.MAX_JSON_BYTES+1];
  for(var req:List.of(request("/api/v1/assistant/sessions/id/attachments","multipart/form-data",huge),request("/api/v1/critic","application/json",huge),request("/api/v1/assistant-other","application/json",huge))){var reached=new AtomicBoolean();filter.doFilter(req,new MockHttpServletResponse(),(r,s)->reached.set(true));assertTrue(reached.get());}
  SecurityContextHolder.clearContext();var reached=new AtomicBoolean();filter.doFilter(request("/api/v1/assistant/sessions","application/json",huge),new MockHttpServletResponse(),(r,s)->reached.set(true));assertTrue(reached.get());
 }
}

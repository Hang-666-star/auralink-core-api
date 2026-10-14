package com.auralink.api.v1.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.auralink.api.v1.error.ApiV1ExceptionHandler;
import com.auralink.assistant.*;
import com.auralink.entity.User;
import com.auralink.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssistantControllerTest {
   final AssistantStore store=mock(AssistantStore.class);
   final CurrentUserService users=mock(CurrentUserService.class);
   final AssistantAttachmentService attachment=mock(AssistantAttachmentService.class);
   final ObjectProvider<AssistantStore> stores=mock(ObjectProvider.class);
   final ObjectProvider<AssistantAttachmentService> attachments=mock(ObjectProvider.class);
   MockMvc mvc;
   @BeforeEach void setup() {
      User user=new User(); user.setId(71L); when(users.requireCurrentUser()).thenReturn(user);
      when(stores.getIfAvailable()).thenReturn(store); when(attachments.getIfAvailable()).thenReturn(attachment);
      mvc=MockMvcBuilders.standaloneSetup(new AssistantController(users,stores,attachments,new ObjectMapper().findAndRegisterModules()))
         .setControllerAdvice(new ApiV1ExceptionHandler()).build();
   }
   @Test void browserOwnerFieldCannotChangeServerResolvedIdentity() throws Exception {
      UUID id=UUID.randomUUID(); Instant now=Instant.now();
      when(store.createSession(71,"test-key-01","测试")).thenReturn(new AssistantRecords.Session(id,"测试",UUID.randomUUID(),0,false,"ACTIVE",now,now));
      mvc.perform(post("/api/v1/assistant/sessions").contentType(MediaType.APPLICATION_JSON)
         .content("{\"requestKey\":\"test-key-01\",\"title\":\"测试\",\"ownerId\":999,\"userId\":999,\"clientId\":\"other\"}"))
         .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.ownerId").doesNotExist());
      verify(store).createSession(71,"test-key-01","测试");
   }
   @Test void manualSuspensionReportsGenerationOnlyAndKeepsExecutionReadsAvailable() throws Exception {
      var service=mock(AssistantExecutionService.class);when(service.properties()).thenReturn(new AssistantExecutionProperties());when(service.generationState()).thenReturn("ASSISTANT_GENERATION_SUSPENDED");when(service.generationAvailable()).thenReturn(false);
      var controller=new AssistantController(users,stores,attachments,new ObjectMapper().findAndRegisterModules());org.springframework.test.util.ReflectionTestUtils.setField(controller,"execution",new AssistantExecutionRuntime(service,null,"READY"));
      var paused=MockMvcBuilders.standaloneSetup(controller).build();paused.perform(get("/api/v1/assistant/status")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.executionAvailable").value(true)).andExpect(jsonPath("$.generationAvailable").value(false)).andExpect(jsonPath("$.generationState").value("ASSISTANT_GENERATION_SUSPENDED"));verifyNoInteractions(attachment);
   }
   @Test void disabledExposesStatusButRejectsFoundationWrites() throws Exception {
      when(stores.getIfAvailable()).thenReturn(null);
      mvc.perform(get("/api/v1/assistant/status")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.executionAvailable").value(false));
      mvc.perform(post("/api/v1/assistant/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"test-key-01\",\"title\":\"测试\"}"))
         .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ASSISTANT_DISABLED"));
      verifyNoInteractions(store);
   }
   @Test void attachmentDtoOmitsInternalStorageAndMediaFailuresRemainJson() throws Exception {
      UUID id=UUID.randomUUID(); UUID session=UUID.randomUUID(); Instant now=Instant.now();
      when(attachment.metadata(71,id)).thenReturn(new AssistantRecords.Attachment(id,session,"PRIVATE_SECRET_PATH","test.png","image/png",20,"a".repeat(64),2,2,"READY",now,now));
      mvc.perform(get("/api/v1/assistant/attachments/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.storageKey").doesNotExist()).andExpect(jsonPath("$.contentUrl").value("/api/v1/assistant/attachments/"+id+"/content"));
      when(attachment.content(71,id)).thenThrow(AssistantFailure.notFound());
      mvc.perform(get("/api/v1/assistant/attachments/"+id+"/content").accept("image/*"))
         .andExpect(status().isNotFound()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.code").value("ASSISTANT_NOT_FOUND"));
   }
   @Test void foreignObjectsAre404AndDoNotExposeDetails() throws Exception {
      UUID id=UUID.randomUUID(); when(store.session(71,id)).thenThrow(AssistantFailure.notFound());
      mvc.perform(get("/api/v1/assistant/sessions/"+id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ASSISTANT_NOT_FOUND"));
   }
   @Test void pagedHistoryUsesServerOwnerAndPublicProjection() throws Exception {
      UUID session=UUID.randomUUID(),branch=UUID.randomUUID(),message=UUID.randomUUID();Instant now=Instant.now();
      when(store.historyPage(71,session,branch,false,50,"next-page")).thenReturn(new AssistantPage<>(List.of(new AssistantRecords.Message(message,session,null,message,null,"USER","saved", "ACCEPTED",null,now,List.of())),null,false));
      mvc.perform(get("/api/v1/assistant/sessions/"+session+"/messages/page").param("branchId",branch.toString()).param("cursor","next-page").param("userId","999"))
         .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items[0].body").value("saved"))
         .andExpect(jsonPath("$.items[0].ownerId").doesNotExist()).andExpect(jsonPath("$.hasMore").value(false));
      verify(store).historyPage(71,session,branch,false,50,"next-page");
   }
   @Test void draftAttachmentsArePrivatePagedDtosWithLifetimeAndServerOwner() throws Exception {
      UUID id=UUID.randomUUID(),session=UUID.randomUUID();Instant now=Instant.now();
      var row=new AssistantRecords.Attachment(id,session,"PRIVATE_PATH","test.png","image/png",20,"a".repeat(64),2,2,"READY",now,now);
      when(store.draftAttachmentPage(71,session,50,null)).thenReturn(new AssistantPage<>(List.of(new AssistantRecords.DraftAttachment(row,now.plusSeconds(86400))),null,false));
      mvc.perform(get("/api/v1/assistant/sessions/"+session+"/attachments/draft").param("userId","999"))
         .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
         .andExpect(jsonPath("$.items[0].id").value(id.toString())).andExpect(jsonPath("$.items[0].draftExpiresAt").exists())
         .andExpect(jsonPath("$.items[0].storageKey").doesNotExist()).andExpect(jsonPath("$.items[0].ownerId").doesNotExist());
      verify(store).draftAttachmentPage(71,session,50,null);
   }
   @Test void uploadResolutionExposesStateWithoutContentOrDispatch() throws Exception {
      UUID id=UUID.randomUUID(),session=UUID.randomUUID();
      when(store.resolveRequest(71,"UPLOAD_ATTACHMENT","upload-resolve-key",session)).thenReturn(new AssistantStore.RequestResolution("UPLOAD_ATTACHMENT","FOUND",session,"ATTACHMENT",id,"FAILED",Instant.now().plusSeconds(86400),false));
      mvc.perform(post("/api/v1/assistant/requests/resolve").contentType(MediaType.APPLICATION_JSON)
         .content("{\"operation\":\"UPLOAD_ATTACHMENT\",\"requestKey\":\"upload-resolve-key\",\"sessionId\":\""+session+"\"}"))
         .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
         .andExpect(jsonPath("$.resultStatus").value("FAILED")).andExpect(jsonPath("$.attachmentLinked").value(false));
      verifyNoInteractions(attachment);
   }
   @Test void csvCellsDefendFormulaPrefixesAndKeepQuotedText() {
      for(String value:List.of("=1+1","  @SUM(1)","\tplain","-1+2","+test","\ntext")) assertThat(AssistantController.csvCell(value)).startsWith("\"'");
      assertThat(AssistantController.csvCell("山水,\"画\"\n下一行")).isEqualTo("\"山水,\"\"画\"\"\n下一行\"");
   }
}

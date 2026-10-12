package com.auralink.api.v1.knowledge;

import com.auralink.api.v1.error.ApiV1ExceptionHandler;
import com.auralink.service.knowledge.KnowledgeSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC JSON contract; actual SecurityConfig authentication is covered by isolated HTTP acceptance. */
class KnowledgeSnapshotControllerTest {
   @Test void disabledMapReturnsExistingSafeUnavailableContractWithoutConfiguration() throws Exception {
      var snapshots = new KnowledgeSnapshotService(new ObjectMapper(), "", "", "", "", false, "filesystem");
      var mvc = MockMvcBuilders.standaloneSetup(new KnowledgeSnapshotController(snapshots))
         .setControllerAdvice(new ApiV1ExceptionHandler()).build();
      mvc.perform(get("/api/v1/knowledge/china-map"))
         .andExpect(status().isServiceUnavailable()).andExpect(content().contentTypeCompatibleWith("application/json"))
         .andExpect(jsonPath("$.code").value("KNOWLEDGE_SNAPSHOT_UNAVAILABLE"))
         .andExpect(jsonPath("$.message").value("此版本未启用地理底图；地域统计仍可读取。"))
         .andExpect(jsonPath("$.features").doesNotExist()).andExpect(jsonPath("$.sha256").doesNotExist());
   }

   @Test void bothSnapshotRoutesRejectQueryParametersBeforeAnyAssetAccess() throws Exception {
      var snapshots = mock(KnowledgeSnapshotService.class);
      var mvc = MockMvcBuilders.standaloneSetup(new KnowledgeSnapshotController(snapshots))
         .setControllerAdvice(new ApiV1ExceptionHandler()).build();
      for (String route : new String[] {"china-map", "poetry-graph"}) {
         mvc.perform(get("/api/v1/knowledge/" + route).param("path", "private.json"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAINTING_QUERY"));
      }
      verifyNoInteractions(snapshots);
   }
}

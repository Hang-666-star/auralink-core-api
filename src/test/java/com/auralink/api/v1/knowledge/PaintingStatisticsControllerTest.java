package com.auralink.api.v1.knowledge;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.api.v1.error.ApiV1ExceptionHandler;
import com.auralink.catalogread.CatalogReadStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC/error contract; real SecurityConfig authentication is separately exercised by local HTTP acceptance. */
class PaintingStatisticsControllerTest {
   @SuppressWarnings("unchecked")
   private static ObjectProvider<CatalogReadStore> provider(CatalogReadStore store) {
      var provider = (ObjectProvider<CatalogReadStore>) mock(ObjectProvider.class);
      when(provider.getIfAvailable()).thenReturn(store);
      return provider;
   }

   @Test void rejectsEveryQueryParameterBeforeStoreAccess() throws Exception {
      var store=mock(CatalogReadStore.class);
      var mvc=MockMvcBuilders.standaloneSetup(new PaintingStatisticsController(provider(store)))
         .setControllerAdvice(new ApiV1ExceptionHandler()).build();
      mvc.perform(get("/api/v1/knowledge/painting-statistics").param("page","0"))
         .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
      verifyNoInteractions(store);
   }

   @Test void disabledStoreIsExplicitlyUnavailable() throws Exception {
      var mvc=MockMvcBuilders.standaloneSetup(new PaintingStatisticsController(provider(null)))
         .setControllerAdvice(new ApiV1ExceptionHandler()).build();
      mvc.perform(get("/api/v1/knowledge/painting-statistics"))
         .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CATALOG_READ_UNAVAILABLE"));
   }

   @Test void returnsContractAndPreventsCaching() throws Exception {
      var store=mock(CatalogReadStore.class);
      when(store.paintingStatistics()).thenReturn(new PaintingStatisticsResponse(1,
         new PaintingStatisticsResponse.Population("gallery-displayable","Gallery 可展示画作",0),
         new PaintingStatisticsResponse.AuthorNames(0,0),Map.of()));
      var mvc=MockMvcBuilders.standaloneSetup(new PaintingStatisticsController(provider(store))).build();
      mvc.perform(get("/api/v1/knowledge/painting-statistics"))
         .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
         .andExpect(jsonPath("$.schemaVersion").value(1)).andExpect(jsonPath("$.population.count").value(0));
   }

   @Test void storageFailureIsJsonWithoutSqlOrConnectionDetails() throws Exception {
      var store=mock(CatalogReadStore.class);
      when(store.paintingStatistics()).thenThrow(new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE,
         ApiErrorCode.CATALOG_READ_UNAVAILABLE,"画作目录暂时不可用"));
      var mvc=MockMvcBuilders.standaloneSetup(new PaintingStatisticsController(provider(store)))
         .setControllerAdvice(new ApiV1ExceptionHandler()).build();
      mvc.perform(get("/api/v1/knowledge/painting-statistics"))
         .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CATALOG_READ_UNAVAILABLE"))
         .andExpect(jsonPath("$.message").value("画作目录暂时不可用"));
   }
}

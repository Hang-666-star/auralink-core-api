package com.auralink.service.painting;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.api.v1.error.ApiV1ExceptionHandler;
import com.auralink.api.v1.painting.PaintingDetailResponse;
import com.auralink.catalogread.CatalogReadStore;
import com.auralink.catalog.DynastyNormalizer;
import com.auralink.security.SecurityConfig;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/** Focused release contracts; real packaged HTTP/PG acceptance is separate. */
class R25ProductionContractTest {
   private static CatalogReadStore.CatalogAnnotation annotation(String key, String visibility, String raw) {
      return new CatalogReadStore.CatalogAnnotation(key, key, "string", null, "source", 1,
         visibility, "none", key, raw, raw, "PRESENT", "SOURCE_SUPPLIED");
   }

   private static PaintingDetailResponse detail(List<CatalogReadStore.CatalogAnnotation> annotations) throws Exception {
      var painting = new CatalogReadStore.CatalogPainting("11111111-1111-4111-8111-111111111111",
         "source-001", 1, "private-source-number", "private-original-path.jpg", "标题", "作者", "花鸟",
         "ACTIVE", "VALID", "MISSING", null, Map.of("legacy.generated_text", "0"), annotations);
      var service = new PaintingQueryService(null, null, null, null, null, new DynastyNormalizer(), null, null);
      Method method = PaintingQueryService.class.getDeclaredMethod("catalogDetail", CatalogReadStore.CatalogPainting.class, boolean.class);
      method.setAccessible(true);
      return (PaintingDetailResponse) method.invoke(service, painting, false);
   }

   @Test void nonPublicMappingsCannotEscapeThroughCompatibilityFields() throws Exception {
      var response = detail(List.of(annotation("source.record_id", "internal", "private-source-number"),
         annotation("source.relative_image", "hidden", "private-original-path.jpg"),
         annotation("business.subject", "public", "0")));
      assertNull(response.sourceSequence());
      assertNull(response.imageStorageName());
      assertEquals("0", response.generatedText());
      assertEquals(1, response.annotations().size());
      assertEquals("0", response.annotations().get(0).rawValue());
   }

   @Test void legacyPublicCompatibilityValuesRemainUsable() throws Exception {
      var response = detail(List.of(annotation("source.original_sequence", "public", "private-source-number")));
      assertEquals("private-source-number", response.sourceSequence());
      assertEquals("private-original-path.jpg", response.imageStorageName());
      assertEquals("0", response.generatedText());
   }

   @Test void mediaBusinessErrorsExplicitlyReturnJsonEvenForBinaryAccept() {
      var request = new MockHttpServletRequest("GET", "/api/v1/assets/11111111-1111-4111-8111-111111111111/content");
      request.addHeader("Accept", "image/*,application/octet-stream");
      var response = new ApiV1ExceptionHandler().handleApiException(
         new ApiV1Exception(HttpStatus.NOT_FOUND, ApiErrorCode.ASSET_NOT_FOUND, "资源不存在"), request);
      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
      assertEquals("ASSET_NOT_FOUND", response.getBody().code());
   }

   @Test void acceptedErrorDispatcherProtectionIsPackaged() throws Exception {
      try (var in = SecurityConfig.class.getResourceAsStream("SecurityConfig.class")) {
         assertNotNull(in);
         String bytecodeConstants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
         assertTrue(bytecodeConstants.contains("dispatcherTypeMatchers"));
         assertTrue(bytecodeConstants.contains("jakarta/servlet/DispatcherType"));
         assertTrue(bytecodeConstants.contains("ERROR"));
      }
   }
}

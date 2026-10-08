package com.auralink.provider.seedream;

import com.auralink.config.properties.CreationProviderProperties;
import com.auralink.config.properties.ProviderProperties;
import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.provider.http.ProviderHttpExecutor;
import com.auralink.provider.http.ProviderHttpResponse;
import com.auralink.provider.validation.StrictProviderJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SeedreamHttpClient {
   private static final long MAX_RESPONSE_BYTES = 1048576L;
   private final RestClient restClient;
   private final ProviderHttpExecutor httpExecutor;
   private final ObjectMapper objectMapper;
   private final CreationProviderProperties creationProperties;
   private final ProviderProperties.Provider provider;
   private final SeedreamEndpointResolver endpointResolver;

   public SeedreamHttpClient(
      @Qualifier("seedreamProviderRestClient") RestClient restClient,
      ProviderHttpExecutor httpExecutor,
      ObjectMapper objectMapper,
      CreationProviderProperties creationProperties,
      ProviderProperties providerProperties,
      SeedreamEndpointResolver endpointResolver
   ) {
      this.restClient = restClient;
      this.httpExecutor = httpExecutor;
      this.objectMapper = objectMapper;
      this.creationProperties = creationProperties;
      this.provider = providerProperties.getSeedream();
      this.endpointResolver = endpointResolver;
   }

   public String generate(String requestId, String prompt, String imageDataUrl) {
      URI endpoint = this.endpointResolver.resolveGenerationEndpoint();
      SeedreamGenerationRequest request = imageDataUrl == null
         ? SeedreamGenerationRequest.text(
            this.provider.getModel().trim(), prompt, this.creationProperties.getSeedreamDefaultSize(), this.creationProperties.isSeedreamWatermark()
         )
         : SeedreamGenerationRequest.image(
            this.provider.getModel().trim(),
            prompt,
            imageDataUrl,
            this.creationProperties.getSeedreamDefaultSize(),
            this.creationProperties.isSeedreamWatermark()
         );
      long maxRequestBytes = Math.addExact(
         Math.multiplyExact(this.creationProperties.getMaxImageInputBytes(), 2L), Math.multiplyExact(this.creationProperties.getMaxTextChars(), 4L)
      );
      ProviderHttpResponse response = this.httpExecutor
         .postJson(this.restClient, endpoint, this.provider.getApiKey(), requestId, request, maxRequestBytes, 1048576L);
      return this.parseSingleUrl(response.body());
   }

   private String parseSingleUrl(byte[] responseBody) {
      JsonNode root;
      try {
         root = StrictProviderJson.parse(this.objectMapper, responseBody);
      } catch (Exception exception) {
         throw this.invalid("Seedream returned malformed JSON", exception);
      }

      JsonNode data = root == null ? null : root.get("data");
      if (data != null && data.isArray() && data.size() == 1 && data.get(0).isObject()) {
         JsonNode item = data.get(0);
         JsonNode urlNode = item.get("url");
         if (urlNode != null && urlNode.isTextual() && !urlNode.textValue().isBlank() && !item.hasNonNull("b64_json")) {
            String url = urlNode.textValue().trim();

            try {
               URI parsed = URI.create(url);
               if (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme())) {
                  throw new IllegalArgumentException("unsupported scheme");
               } else {
                  return url;
               }
            } catch (RuntimeException exception) {
               throw this.invalid("Seedream image URL is invalid", exception);
            }
         } else {
            throw this.invalid("Seedream image result is missing or ambiguous", null);
         }
      } else {
         throw this.invalid("Seedream must return exactly one image result", null);
      }
   }

   private ProviderExecutionException invalid(String message, Throwable cause) {
      return cause == null
         ? new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INVALID_RESPONSE, message)
         : new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INVALID_RESPONSE, message, cause);
   }
}

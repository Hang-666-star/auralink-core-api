package com.auralink.critic.provider;

import com.auralink.config.properties.CriticProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient.RequestBodySpec;

@Component
public class PythonCriticProvider implements CriticEvaluationProvider {
   public static final String INTERNAL_TOKEN_HEADER = "X-Auralink-Critic-Token";
   private final RestClient client;
   private final CriticProperties properties;

   public PythonCriticProvider(@Qualifier("criticRestClient") RestClient client, CriticProperties properties) {
      this.client = client;
      this.properties = properties;
   }

   @Override
   public JsonNode evaluate(CriticEvaluationProvider.Request request) {
      if (!this.properties.isConfigured()) {
         throw new CriticProviderException(CriticProviderException.Failure.UNAVAILABLE, "Critic service is not configured");
      }

      if (request != null && request.image() != null && request.image().exists()) {
         MultiValueMap<String, Object> form = new LinkedMultiValueMap();
         form.add("image", request.image());
         form.add("profile", request.profile());
         if (request.title() != null && !request.title().isBlank()) {
            form.add("title", request.title());
         }

         try {
            URI endpoint = URI.create(this.properties.getServiceUrl()).resolve("/api/v1/evaluations");
            JsonNode response = (JsonNode)((RequestBodySpec)((RequestBodySpec)((RequestBodySpec)this.client.post().uri(endpoint))
                     .contentType(MediaType.MULTIPART_FORM_DATA)
                     .accept(new MediaType[]{MediaType.APPLICATION_JSON}))
                  .header("X-Auralink-Critic-Token", new String[]{this.properties.getInternalToken()}))
               .body(form)
               .retrieve()
               .body(JsonNode.class);
            if (response != null && response.isObject()) {
               return response;
            } else {
               throw new CriticProviderException(CriticProviderException.Failure.INVALID_RESPONSE, "Critic returned no JSON object");
            }
         } catch (ResourceAccessException exception) {
            throw new CriticProviderException(CriticProviderException.Failure.TIMEOUT, "Critic service timed out or could not be reached", exception);
         } catch (RestClientResponseException exception) {
            throw new CriticProviderException(CriticProviderException.Failure.UNAVAILABLE, "Critic service rejected the internal request", exception);
         } catch (IllegalArgumentException exception) {
            throw new CriticProviderException(CriticProviderException.Failure.INVALID_RESPONSE, "Critic service URL is invalid", exception);
         }
      } else {
         throw new CriticProviderException(CriticProviderException.Failure.INVALID_RESPONSE, "Critic source image is unavailable");
      }
   }
}

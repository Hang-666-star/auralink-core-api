package com.auralink.guide.provider;

import com.auralink.config.properties.GuideProperties;
import com.auralink.guide.context.PaintingGuideContext;
import com.auralink.guide.knowledge.KnowledgeItem;
import com.auralink.guide.model.GuideResult;
import com.auralink.guide.model.GuideResultCodec;
import com.auralink.guide.model.GuideResultValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient.RequestBodySpec;

@Component
public class PythonGuideProvider implements GuideProvider {
   static final String INTERNAL_TOKEN_HEADER = "X-Auralink-Internal-Token";
   static final int MAX_INTERNAL_RESPONSE_BYTES = 262144;
   private final RestClient restClient;
   private final GuideProperties properties;
   private final GuideResultCodec resultCodec;
   private final ObjectMapper objectMapper;
   private final ObjectReader envelopeReader;

   public PythonGuideProvider(
      @Qualifier("guideRestClient") RestClient restClient, GuideProperties properties, GuideResultCodec resultCodec, ObjectMapper objectMapper
   ) {
      this.restClient = Objects.requireNonNull(restClient, "restClient");
      this.properties = Objects.requireNonNull(properties, "properties");
      this.resultCodec = Objects.requireNonNull(resultCodec, "resultCodec");
      this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
      this.envelopeReader = this.objectMapper
         .copy()
         .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
         .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
         .readerFor(PythonGuideProvider.ResponseEnvelope.class);
   }

   @Override
   public GuideGenerationResult generate(String requestId, PaintingGuideContext context) {
      String canonicalRequestId = this.requireCanonicalRequestId(requestId);
      PaintingGuideContext safeContext = Objects.requireNonNull(context, "context");
      String token = this.requireConfiguration(this.properties.getInternalToken(), "internal token");
      String schemaVersion = this.requireConfiguration(this.properties.getSchemaVersion(), "schema version");
      URI endpoint = this.endpoint();
      PythonGuideProvider.GenerateRequest request = new PythonGuideProvider.GenerateRequest(
         canonicalRequestId,
         schemaVersion,
         new PythonGuideProvider.PaintingPayload(
            safeContext.paintingId(), safeContext.basic(), safeContext.artist(), safeContext.art(), safeContext.officialAnnotations()
         ),
         safeContext.knowledge() == null ? List.of() : List.copyOf(safeContext.knowledge()),
         new PythonGuideProvider.GenerationOptions("zh-CN", "STANDARD")
      );
      return this.execute(endpoint, token, request, safeContext.knowledge());
   }

   private GuideGenerationResult execute(URI endpoint, String token, PythonGuideProvider.GenerateRequest request, List<KnowledgeItem> allowedKnowledge) {
      try {
         PythonGuideProvider.InternalResponse response = (PythonGuideProvider.InternalResponse)((RequestBodySpec)((RequestBodySpec)((RequestBodySpec)this.restClient
                     .post()
                     .uri(endpoint))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(new MediaType[]{MediaType.APPLICATION_JSON}))
               .header("X-Auralink-Internal-Token", new String[]{token}))
            .body(request)
            .exchange((ignoredRequest, clientResponse) -> {
               HttpStatusCode status = clientResponse.getStatusCode();

               byte[] body;
               try {
                  body = this.readBounded(clientResponse.getBody(), clientResponse.getHeaders());
               } catch (IOException | GuideProviderException exception) {
                  if (status.is2xxSuccessful()) {
                     throw exception;
                  }

                  body = new byte[0];
               }

               return new PythonGuideProvider.InternalResponse(status, body);
            });
         return this.parseResponse(response, request.requestId(), request.schemaVersion(), allowedKnowledge);
      } catch (GuideProviderException exception) {
         throw exception;
      } catch (ResourceAccessException exception) {
         if (this.hasCause(exception, SocketTimeoutException.class)) {
            throw new GuideProviderException(GuideProviderException.Failure.TIMEOUT, false, "Guide service timed out", exception);
         } else {
            throw new GuideProviderException(GuideProviderException.Failure.UNAVAILABLE, false, "Guide service is unavailable", exception);
         }
      } catch (RestClientException exception) {
         throw new GuideProviderException(GuideProviderException.Failure.UNAVAILABLE, false, "Guide service request failed", exception);
      }
   }

   private GuideGenerationResult parseResponse(
      PythonGuideProvider.InternalResponse response, String requestId, String schemaVersion, List<KnowledgeItem> allowedKnowledge
   ) {
      PythonGuideProvider.ResponseEnvelope envelope = null;
      if (response.body().length > 0) {
         try {
            envelope = (PythonGuideProvider.ResponseEnvelope)this.envelopeReader.readValue(response.body());
         } catch (IOException exception) {
            if (response.status().is2xxSuccessful()) {
               throw this.invalidResponse("Guide service returned malformed JSON", exception);
            }
         }
      }

      if (envelope != null && requestId.equals(envelope.requestId()) && "FAILED".equals(envelope.status())) {
         throw this.failureEnvelope(envelope);
      }

      if (!response.status().is2xxSuccessful()) {
         throw this.httpFailure(response.status());
      }

      if (envelope == null || !requestId.equals(envelope.requestId())) {
         throw this.invalidResponse("Guide service requestId did not match");
      }

      if ("SUCCESS".equals(envelope.status()) && this.hasResult(envelope) && envelope.error() == null) {
         try {
            GuideResult result = this.resultCodec
               .decode(this.objectMapper.writeValueAsString(envelope.result()), schemaVersion, allowedKnowledge == null ? List.of() : allowedKnowledge);
            return new GuideGenerationResult(requestId, result);
         } catch (GuideResultValidationException | JsonProcessingException exception) {
            throw this.invalidResponse("Guide service returned an invalid structured result", exception);
         }
      } else {
         throw this.invalidResponse("Guide service returned an invalid response envelope");
      }
   }

   private GuideProviderException failureEnvelope(PythonGuideProvider.ResponseEnvelope envelope) {
      if (!this.hasResult(envelope) && envelope.error() != null && envelope.error().code() != null) {
         String code = envelope.error().code().trim().toUpperCase(Locale.ROOT);

         return switch (code) {
            case "PROVIDER_NOT_CONFIGURED", "SERVICE_NOT_CONFIGURED" -> new GuideProviderException(
               GuideProviderException.Failure.CONFIGURATION, false, "Guide provider is not configured"
            );
            case "UNAUTHORIZED_INTERNAL_CALL" -> new GuideProviderException(
               GuideProviderException.Failure.CONFIGURATION, false, "Guide service authentication is not configured correctly"
            );
            case "INTERNAL_SERVICE_ERROR" -> new GuideProviderException(
               GuideProviderException.Failure.UNAVAILABLE, false, "Guide service is temporarily unavailable"
            );
            case "UPSTREAM_TIMEOUT", "TIMEOUT" -> new GuideProviderException(GuideProviderException.Failure.TIMEOUT, false, "Guide provider timed out");
            case "UPSTREAM_RATE_LIMIT", "UPSTREAM_UNAVAILABLE", "UPSTREAM_SERVER_ERROR" -> new GuideProviderException(
               GuideProviderException.Failure.UNAVAILABLE, false, "Guide provider is temporarily unavailable"
            );
            case "INVALID_RESULT", "INVALID_PROVIDER_RESPONSE" -> this.invalidResponse("Guide provider returned an invalid structured result");
            case "INVALID_REQUEST", "REQUEST_TOO_LARGE" -> this.invalidResponse("Guide service rejected the internal contract");
            case "UPSTREAM_REJECTED" -> new GuideProviderException(GuideProviderException.Failure.REJECTED, false, "Guide provider rejected the request");
            default -> new GuideProviderException(GuideProviderException.Failure.REJECTED, false, "Guide provider rejected the request");
         };
      } else {
         return this.invalidResponse("Guide service returned an invalid failure envelope");
      }
   }

   private boolean hasResult(PythonGuideProvider.ResponseEnvelope envelope) {
      return envelope.result() != null && !envelope.result().isNull();
   }

   private GuideProviderException httpFailure(HttpStatusCode status) {
      int value = status.value();
      if (value == 408 || value == 504) {
         return new GuideProviderException(GuideProviderException.Failure.TIMEOUT, false, "Guide service timed out");
      } else if (value == 429 || value == 502 || value == 503 || value >= 500) {
         return new GuideProviderException(GuideProviderException.Failure.UNAVAILABLE, false, "Guide service is temporarily unavailable");
      } else if (value == 401 || value == 403) {
         return new GuideProviderException(GuideProviderException.Failure.CONFIGURATION, false, "Guide service authentication is not configured correctly");
      } else {
         return value != 400 && value != 413 && value != 422
            ? new GuideProviderException(GuideProviderException.Failure.UNAVAILABLE, false, "Guide service request failed")
            : new GuideProviderException(GuideProviderException.Failure.INVALID_RESPONSE, false, "Guide service rejected the internal contract");
      }
   }

   private byte[] readBounded(InputStream input, HttpHeaders headers) {
      if (input == null) {
         return new byte[0];
      }

      long contentLength = headers.getContentLength();
      if (contentLength > 262144L) {
         throw this.invalidResponse("Guide service response exceeded the byte limit");
      }

      try (
         InputStream source = input;
         ByteArrayOutputStream output = new ByteArrayOutputStream();
      ) {
         byte[] buffer = new byte[8192];
         int total = 0;

         int read;
         while ((read = source.read(buffer)) != -1) {
            total += read;
            if (total > 262144) {
               throw this.invalidResponse("Guide service response exceeded the byte limit");
            }

            output.write(buffer, 0, read);
         }

         return output.toByteArray();
      } catch (GuideProviderException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new GuideProviderException(GuideProviderException.Failure.UNAVAILABLE, false, "Guide service response could not be read", exception);
      }
   }

   private URI endpoint() {
      String configured = this.requireConfiguration(Objects.toString(this.properties.getServiceUrl(), null), "service URL");

      try {
         URI base = new URI(configured);
         String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
         if (("http".equals(scheme) || "https".equals(scheme))
            && base.getHost() != null
            && this.isLoopbackLiteral(base.getHost())
            && base.getUserInfo() == null
            && base.getQuery() == null
            && base.getFragment() == null) {
            return base.resolve("/v1/guide/generate");
         } else {
            throw this.configuration("Guide service URL is invalid");
         }
      } catch (URISyntaxException exception) {
         throw new GuideProviderException(GuideProviderException.Failure.CONFIGURATION, false, "Guide service URL is invalid", exception);
      }
   }

   private boolean isLoopbackLiteral(String host) {
      String normalized = host.toLowerCase(Locale.ROOT);
      if (normalized.startsWith("[") && normalized.endsWith("]")) {
         normalized = normalized.substring(1, normalized.length() - 1);
      }

      return "127.0.0.1".equals(normalized) || "::1".equals(normalized);
   }

   private String requireCanonicalRequestId(String requestId) {
      if (requestId != null && !requestId.isBlank()) {
         try {
            String canonical = UUID.fromString(requestId).toString();
            if (!canonical.equals(requestId.toLowerCase(Locale.ROOT))) {
               throw this.configuration("Guide requestId is invalid");
            } else {
               return canonical;
            }
         } catch (IllegalArgumentException exception) {
            throw this.configuration("Guide requestId is invalid");
         }
      } else {
         throw this.configuration("Guide requestId is invalid");
      }
   }

   private String requireConfiguration(String value, String name) {
      if (value != null && !value.isBlank()) {
         return value;
      } else {
         throw this.configuration("Guide " + name + " is not configured");
      }
   }

   private GuideProviderException configuration(String message) {
      return new GuideProviderException(GuideProviderException.Failure.CONFIGURATION, false, message);
   }

   private GuideProviderException invalidResponse(String message) {
      return new GuideProviderException(GuideProviderException.Failure.INVALID_RESPONSE, false, message);
   }

   private GuideProviderException invalidResponse(String message, Throwable cause) {
      return new GuideProviderException(GuideProviderException.Failure.INVALID_RESPONSE, false, message, cause);
   }

   private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
      for (Throwable current = throwable; current != null; current = current.getCause()) {
         if (type.isInstance(current)) {
            return true;
         }
      }

      return false;
   }

   private record ErrorEnvelope(String code, String message) {
   }

   private record GenerateRequest(
      String requestId,
      String schemaVersion,
      PythonGuideProvider.PaintingPayload painting,
      List<KnowledgeItem> knowledge,
      PythonGuideProvider.GenerationOptions options
   ) {
   }

   private record GenerationOptions(String language, String detailLevel) {
   }

   private record InternalResponse(HttpStatusCode status, byte[] body) {
   }

   private record PaintingPayload(String paintingId, Object basic, Object artist, Object art, Object officialAnnotations) {
   }

   private record ResponseEnvelope(String requestId, String status, JsonNode result, PythonGuideProvider.ErrorEnvelope error) {
   }
}

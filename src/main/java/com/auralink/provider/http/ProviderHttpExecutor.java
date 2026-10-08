package com.auralink.provider.http;

import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import com.auralink.provider.validation.StrictProviderJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient.RequestBodySpec;

@Component
public class ProviderHttpExecutor {
   private static final int BUFFER_SIZE = 16384;
   private static final int MAX_REQUEST_ID_CHARS = 512;
   private static final Pattern SAFE_PROVIDER_ERROR_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
   private final ObjectMapper objectMapper;

   public ProviderHttpResponse postJson(
      RestClient client, URI endpoint, String bearerToken, String requestId, Object request, long maxRequestBytes, long maxResponseBytes
   ) {
      if (client != null && endpoint != null && requestId != null && !requestId.isBlank() && request != null && maxRequestBytes >= 1L && maxResponseBytes >= 1L
         )
       {
         byte[] requestBytes;
         try {
            requestBytes = this.objectMapper.writeValueAsBytes(request);
         } catch (JsonProcessingException exception) {
            throw this.internal("Provider request could not be serialized", exception);
         }

         if (requestBytes.length > maxRequestBytes) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_REJECTED, "Provider request exceeds the configured byte limit");
         }

         try {
            ProviderHttpResponse response = (ProviderHttpResponse)((RequestBodySpec)((RequestBodySpec)((RequestBodySpec)client.post().uri(endpoint))
                     .contentType(MediaType.APPLICATION_JSON)
                     .accept(new MediaType[]{MediaType.APPLICATION_JSON}))
                  .headers(headers -> {
                     this.setBearer(headers, bearerToken);
                     headers.set("X-Auralink-Request-Id", requestId);
                  }))
               .body(requestBytes)
               .exchange(
                  (httpRequest, httpResponse) -> {
                     long declaredLength = httpResponse.getHeaders().getContentLength();
                     if (declaredLength > maxResponseBytes) {
                        throw new ProviderExecutionException(
                           ProviderErrorCategory.PROVIDER_INVALID_RESPONSE, "Provider response exceeds the configured byte limit"
                        );
                     }

                     try (InputStream input = httpResponse.getBody()) {
                        return new ProviderHttpResponse(
                           httpResponse.getStatusCode().value(), this.readBounded(input, maxResponseBytes), this.safeRequestId(httpResponse.getHeaders())
                        );
                     }
                  }
               );
            this.requireSuccessfulStatus(response);
            return response;
         } catch (ProviderExecutionException exception) {
            throw exception;
         } catch (ResourceAccessException exception) {
            if (this.containsTimeout(exception)) {
               throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_TIMEOUT, "Provider request timed out", exception);
            } else {
               throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_UNAVAILABLE, "Provider service is unavailable", exception);
            }
         } catch (RestClientException exception) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_UNAVAILABLE, "Provider transport failed", exception);
         }
      } else {
         throw this.internal("Provider HTTP contract is incomplete", null);
      }
   }

   private byte[] readBounded(InputStream input, long maxBytes) throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[16384];
      long total = 0L;

      while (true) {
         int read = input.read(buffer);
         if (read < 0) {
            return output.toByteArray();
         }

         if (read != 0) {
            if (total > maxBytes - read) {
               throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INVALID_RESPONSE, "Provider response exceeds the configured byte limit");
            }

            output.write(buffer, 0, read);
            total += read;
         }
      }
   }

   private void requireSuccessfulStatus(ProviderHttpResponse response) {
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
         ProviderErrorCategory category;
         String safeMessage;
         if (status == 401 || status == 403) {
            category = ProviderErrorCategory.PROVIDER_CONFIGURATION_INVALID;
            safeMessage = "Provider authentication was rejected";
         } else if (status == 429) {
            category = ProviderErrorCategory.PROVIDER_RATE_LIMITED;
            safeMessage = "Provider rate limit was reached";
         } else if (status >= 500) {
            category = ProviderErrorCategory.PROVIDER_UNAVAILABLE;
            safeMessage = "Provider service returned a failure";
         } else {
            category = ProviderErrorCategory.PROVIDER_REJECTED;
            safeMessage = "Provider rejected the request";
         }

         ProviderHttpExecutor.SafeProviderError safe = this.safeProviderError(response);
         throw ProviderExecutionException.fromProviderResponse(category, safeMessage, status, safe.errorCode(), safe.safeRequestId());
      }
   }

   private ProviderHttpExecutor.SafeProviderError safeProviderError(ProviderHttpResponse response) {
      String errorCode = null;
      String safeRequestId = response.safeRequestId();

      try {
         JsonNode root = StrictProviderJson.parse(this.objectMapper, response.body());
         if (root != null && root.isObject()) {
            JsonNode error = root.get("error");
            JsonNode code = error != null && error.isObject() ? error.get("code") : root.get("code");
            errorCode = this.safeErrorCode(code);
            if (safeRequestId == null) {
               JsonNode requestId = error != null && error.isObject() ? this.firstPresent(error.get("request_id"), error.get("requestId")) : null;
               requestId = this.firstPresent(requestId, root.get("request_id"), root.get("requestId"));
               safeRequestId = requestId != null && requestId.isTextual() ? this.hashRequestId(requestId.textValue()) : null;
            }
         }
      } catch (Exception var8) {
      }

      return new ProviderHttpExecutor.SafeProviderError(errorCode, safeRequestId);
   }

   private JsonNode firstPresent(JsonNode... candidates) {
      for (JsonNode candidate : candidates) {
         if (candidate != null && !candidate.isNull()) {
            return candidate;
         }
      }

      return null;
   }

   private String safeErrorCode(JsonNode code) {
      if (code != null && code.isTextual()) {
         String value = code.textValue().trim();
         return SAFE_PROVIDER_ERROR_CODE.matcher(value).matches() ? value : null;
      } else {
         return null;
      }
   }

   private String safeRequestId(HttpHeaders headers) {
      String value = headers.getFirst("X-Request-Id");
      if (value == null || value.isBlank()) {
         value = headers.getFirst("X-Tt-Logid");
      }

      return this.hashRequestId(value);
   }

   private String hashRequestId(String value) {
      if (value == null) {
         return null;
      }

      String normalized = value.trim();
      if (!normalized.isEmpty() && normalized.length() <= 512 && !this.containsControl(normalized)) {
         try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 16);
         } catch (NoSuchAlgorithmException exception) {
            throw this.internal("Provider response metadata could not be sanitized", exception);
         }
      } else {
         return null;
      }
   }

   private void setBearer(HttpHeaders headers, String bearerToken) {
      if (bearerToken != null) {
         if (!bearerToken.isBlank() && !this.containsControl(bearerToken)) {
            headers.set("Authorization", "Bearer " + bearerToken);
         } else {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_CONFIGURATION_INVALID, "Provider authentication configuration is invalid");
         }
      }
   }

   private boolean containsControl(String value) {
      return value.chars().anyMatch(character -> Character.isISOControl((char)character));
   }

   private boolean containsTimeout(Throwable throwable) {
      for (Throwable current = throwable; current != null; current = current.getCause()) {
         if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
            return true;
         }
      }

      return false;
   }

   private ProviderExecutionException internal(String message, Throwable cause) {
      return cause == null
         ? new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, message)
         : new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, message, cause);
   }

   public ProviderHttpExecutor(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
   }

   private record SafeProviderError(String errorCode, String safeRequestId) {
   }
}

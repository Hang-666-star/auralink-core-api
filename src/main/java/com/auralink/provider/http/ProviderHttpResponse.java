package com.auralink.provider.http;

public record ProviderHttpResponse(int statusCode, byte[] body, String safeRequestId) {
   public ProviderHttpResponse(int statusCode, byte[] body) {
      this(statusCode, body, null);
   }

   public ProviderHttpResponse {
      body = (byte[])body.clone();
   }

   public byte[] body() {
      return (byte[])this.body.clone();
   }
}

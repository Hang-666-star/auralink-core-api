package com.auralink.service.media;

import com.auralink.entity.User;
import java.io.InputStream;

public record GeneratedAssetRequest(
   User owner, InputStream content, String originalFilename, String mimeType, String assetType, String semanticType, Double durationSeconds
) {
   public GeneratedAssetRequest {
      if (owner == null) {
         throw new IllegalArgumentException("Generated asset owner is required");
      }

      if (content == null) {
         throw new IllegalArgumentException("Generated asset content is required");
      }
   }
}

package com.auralink.api.v1.creation;

import java.util.List;

public record CreationPoemResponse(String schemaVersion, String title, List<String> lines, String text) {
   public CreationPoemResponse {
      lines = List.copyOf(lines);
   }
}

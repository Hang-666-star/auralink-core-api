package com.auralink.creation.provider;

import java.util.List;

public record ProviderTextOutput(String schemaVersion, String title, List<String> lines, String text) implements ProviderOutput {
   public ProviderTextOutput {
      lines = List.copyOf(lines);
   }
}

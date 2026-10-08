package com.auralink.provider.artifact;

import java.nio.file.Path;

@FunctionalInterface
public interface ProviderArtifactWriter {
   void write(Path controlledTarget) throws Exception;
}

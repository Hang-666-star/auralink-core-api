package com.auralink.provider.seedream;

import java.net.URI;

@FunctionalInterface
public interface SeedreamEndpointResolver {
   URI resolveGenerationEndpoint();
}

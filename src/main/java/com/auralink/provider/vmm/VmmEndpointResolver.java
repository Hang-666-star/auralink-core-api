package com.auralink.provider.vmm;

import java.net.URI;
import java.nio.file.Path;

public interface VmmEndpointResolver {
   URI resolveGenerationEndpoint();

   Path resolveOutputRoot();
}

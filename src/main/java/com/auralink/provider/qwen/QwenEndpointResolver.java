package com.auralink.provider.qwen;

import java.net.URI;

@FunctionalInterface
public interface QwenEndpointResolver {
   URI resolveChatCompletionsEndpoint();
}

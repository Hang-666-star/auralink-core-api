package com.auralink.creation.provider;

import java.util.List;

public interface CreationProviderAdapter {
   List<ProviderAdapterBinding> bindings();

   ProviderReadiness readiness();

   ProviderExecutionResult execute(ProviderExecutionRequest request);
}

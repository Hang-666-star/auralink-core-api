package com.auralink.creation.provider;

import com.auralink.workflow.WorkflowOperation;
import com.auralink.workflow.capability.WorkflowCapabilityRegistry;
import com.auralink.workflow.capability.WorkflowOperationCapability;
import com.auralink.workflow.capability.WorkflowProviderCapability;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProviderAdapterRegistry {
   private final Map<ProviderAdapterRegistry.Key, ProviderAdapterRegistry.RegisteredAdapter> adapters;

   public ProviderAdapterRegistry(List<CreationProviderAdapter> implementations, WorkflowCapabilityRegistry workflowCapabilities) {
      boolean packagedMockActive = implementations.stream().anyMatch(PackagedMockCreationProviderAdapter.class::isInstance);
      LinkedHashMap<ProviderAdapterRegistry.Key, ProviderAdapterRegistry.RegisteredAdapter> registered = new LinkedHashMap<>();

      for (CreationProviderAdapter adapter : implementations) {
         if (adapter == null || adapter.bindings() == null || adapter.bindings().isEmpty()) {
            throw new IllegalStateException("Creation provider adapter has no binding");
         }

         if (!packagedMockActive
            || adapter instanceof PackagedMockCreationProviderAdapter
            || !adapter.bindings().stream().anyMatch(bindingx -> bindingx.operation() != WorkflowOperation.PAINTING_TO_MUSIC)) {
            for (ProviderAdapterBinding binding : adapter.bindings()) {
               ProviderAdapterRegistry.Key key = new ProviderAdapterRegistry.Key(binding.operation(), binding.providerCode());
               if (registered.putIfAbsent(key, new ProviderAdapterRegistry.RegisteredAdapter(adapter, binding)) != null) {
                  throw new IllegalStateException("Duplicate creation provider adapter binding");
               }
            }
         }
      }

      this.crossCheckWorkflowCapabilities(registered, workflowCapabilities);
      this.adapters = Map.copyOf(registered);
   }

   public Optional<CreationProviderAdapter> find(WorkflowOperation operation, String providerCode) {
      if (operation != null && providerCode != null && !providerCode.isBlank()) {
         ProviderAdapterRegistry.RegisteredAdapter registered = this.adapters.get(new ProviderAdapterRegistry.Key(operation, providerCode));
         return registered == null ? Optional.empty() : Optional.of(registered.adapter());
      } else {
         return Optional.empty();
      }
   }

   public CreationProviderAdapter require(WorkflowOperation operation, String providerCode) {
      return this.find(operation, providerCode)
         .orElseThrow(
            () -> new ProviderExecutionException(ProviderErrorCategory.PROVIDER_INTERNAL_CONTRACT_ERROR, "No exact creation provider adapter is registered")
         );
   }

   public ProviderReadiness implementationReadiness(WorkflowOperation operation, String providerCode) {
      return operation == WorkflowOperation.PAINTING_TO_VIDEO && "reserved-video".equals(providerCode)
         ? ProviderReadiness.reservedDisabled()
         : this.find(operation, providerCode)
            .map(ignored -> ProviderReadiness.implemented())
            .orElseGet(() -> new ProviderReadiness(ProviderReadinessState.CONFIGURATION_INVALID, "PROVIDER_ADAPTER_NOT_REGISTERED"));
   }

   public ProviderReadiness readiness(WorkflowOperation operation, String providerCode) {
      return operation == WorkflowOperation.PAINTING_TO_VIDEO && "reserved-video".equals(providerCode)
         ? ProviderReadiness.reservedDisabled()
         : this.require(operation, providerCode).readiness();
   }

   public List<ProviderAdapterBinding> bindings() {
      return this.adapters
         .values()
         .stream()
         .map(ProviderAdapterRegistry.RegisteredAdapter::binding)
         .sorted(
            Comparator.<ProviderAdapterBinding, Integer>comparing(binding -> binding.operation().ordinal()).thenComparing(ProviderAdapterBinding::providerCode)
         )
         .toList();
   }

   private void crossCheckWorkflowCapabilities(
      Map<ProviderAdapterRegistry.Key, ProviderAdapterRegistry.RegisteredAdapter> registered, WorkflowCapabilityRegistry workflowCapabilities
   ) {
      int expectedEnabledBindings = 0;

      for (WorkflowOperationCapability operation : workflowCapabilities.operations()) {
         for (WorkflowProviderCapability provider : operation.providers()) {
            ProviderAdapterRegistry.Key key = new ProviderAdapterRegistry.Key(operation.operation(), provider.code());
            ProviderAdapterRegistry.RegisteredAdapter adapter = registered.get(key);
            if (operation.definitionEnabled() && provider.definitionEnabled()) {
               expectedEnabledBindings++;
               if (adapter == null
                  || adapter.binding().inputModality() != operation.inputModality()
                  || adapter.binding().outputModality() != operation.outputModality()) {
                  throw new IllegalStateException("Workflow capability and creation provider registry are inconsistent");
               }
            } else if (adapter != null) {
               throw new IllegalStateException("Disabled workflow provider must not have a creation adapter");
            }
         }
      }

      if (registered.size() != expectedEnabledBindings) {
         throw new IllegalStateException("Creation provider registry contains an unknown binding");
      }
   }

   private record Key(WorkflowOperation operation, String providerCode) {
   }

   private record RegisteredAdapter(CreationProviderAdapter adapter, ProviderAdapterBinding binding) {
   }
}

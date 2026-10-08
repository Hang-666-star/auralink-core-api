package com.auralink.creation;

import com.auralink.ops.round9b2.Round9B2MockCreationProviderAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("catalog-postgres-creation-integration")
@RequestMapping("/internal/local-creation-stub")
public class CreationLocalStubController {
   private final Round9B2MockCreationProviderAdapter provider;
   private final CreationLocalIntegrationConfiguration.LocalControl control;

   public CreationLocalStubController(Round9B2MockCreationProviderAdapter provider, CreationLocalIntegrationConfiguration.LocalControl control) {
      this.provider = provider;
      this.control = control;
   }

   @GetMapping
   public Map<String, Object> state() {
      Map<String, Object> state = new LinkedHashMap<>();
      state.put("seedreamRequests", this.provider.seedreamCalls());
      state.put("qwenRequests", this.provider.qwenCalls());
      state.put("vmmRequests", this.provider.vmmCalls());
      state.put("externalProviderCalls", 0);
      state.put("nextBoundaryFailure", this.control.nextFailure());
      return state;
   }

   @PostMapping("/fail-provider-next")
   public ResponseEntity<Void> failProviderNext() {
      this.provider.failNextCall();
      return ResponseEntity.noContent().build();
   }

   @PostMapping("/fail-boundary-next")
   public ResponseEntity<Map<String, String>> failBoundaryNext(@RequestParam String boundary) {
      try {
         CreationExecutionBoundary selected = CreationExecutionBoundary.valueOf(boundary);
         this.control.failAt(selected);
         return ResponseEntity.ok(Map.of("boundary", selected.name()));
      } catch (IllegalArgumentException exception) {
         return ResponseEntity.badRequest().body(Map.of("error", "unknown local boundary"));
      }
   }
}

package com.auralink.critic.service;

import com.auralink.critic.provider.CriticProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CriticResponseValidator {
   private static final List<String> AUTHORITATIVE = List.of("qiyun_yijing", "bimo_se_cai_zaoxing", "style_tradition_innovation", "composition_space");
   private static final List<String> REQUESTED = List.of("work_quality", "painting_technique", "style", "composition");
   private final ObjectMapper mapper;

   public CriticResponseValidator(ObjectMapper mapper) {
      this.mapper = mapper;
   }

   public String canonicalize(JsonNode root, String profile) {
      if (root == null
         || !root.isObject()
         || root.path("meta").path("scoring_used").asBoolean(true)
         || !profile.equals(root.path("meta").path("profile").asText())
         || !root.path("report").isObject()) {
         fail();
      }

      JsonNode dimensions = root.path("report").path("dimension_assessments");
      Set<String> ids = new LinkedHashSet<>();
      if (!dimensions.isArray()) {
         fail();
      }

      dimensions.forEach(node -> {
         if (!node.path("dimension_id").isTextual() || !node.path("conclusion").isTextual() || node.path("conclusion").asText().isBlank()) {
            fail();
         }

         ids.add(node.path("dimension_id").asText());
      });
      if (!ids.equals(new LinkedHashSet<>("authoritative_four".equals(profile) ? AUTHORITATIVE : REQUESTED))) {
         fail();
      }

      if (!root.path("expert_assessments").isArray() || root.path("expert_assessments").size() != 4) {
         fail();
      }

      try {
         return this.mapper.writeValueAsString(root);
      } catch (Exception exception) {
         throw new CriticProviderException(CriticProviderException.Failure.INVALID_RESPONSE, "Critic result cannot be persisted", exception);
      }
   }

   private static void fail() {
      throw new CriticProviderException(CriticProviderException.Failure.INVALID_RESPONSE, "Critic result does not match the score-free four-dimension contract");
   }
}

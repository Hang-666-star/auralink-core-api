package com.auralink.critic.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.FileSystemResource;

public interface CriticEvaluationProvider {
   JsonNode evaluate(CriticEvaluationProvider.Request request);

   record Request(String profile, String title, FileSystemResource image) {
   }
}

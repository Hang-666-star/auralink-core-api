package com.auralink.creation.provider;

import com.auralink.workflow.WorkflowModality;

public record ProviderTextInput(String text, WorkflowModality modality) implements ProviderInput {
   public ProviderTextInput {
      if (text == null || modality == null) {
         throw new IllegalArgumentException("Provider text and modality are required");
      }

      if (modality != WorkflowModality.TEXT_DESCRIPTION && modality != WorkflowModality.POEM) {
         throw new IllegalArgumentException("Provider text modality must be TEXT_DESCRIPTION or POEM");
      }
   }
}

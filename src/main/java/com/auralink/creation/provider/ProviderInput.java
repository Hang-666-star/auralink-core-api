package com.auralink.creation.provider;

import com.auralink.workflow.WorkflowModality;

public sealed interface ProviderInput permits ProviderTextInput, ProviderImageInput {
   WorkflowModality modality();
}

package com.auralink.provider.qwen;

public enum QwenResponseValidationStage {
   HTTP_ENVELOPE,
   CHOICES,
   MESSAGE,
   CONTENT,
   JSON_SYNTAX,
   JSON_STRUCTURE,
   POEM_SCHEMA,
   POEM_SEMANTICS;
}

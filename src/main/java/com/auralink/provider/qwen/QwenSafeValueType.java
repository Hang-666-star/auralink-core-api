package com.auralink.provider.qwen;

import com.fasterxml.jackson.databind.JsonNode;

public enum QwenSafeValueType {
   OBJECT,
   ARRAY,
   STRING,
   NUMBER,
   BOOLEAN,
   NULL,
   BINARY,
   OTHER;

   static QwenSafeValueType from(JsonNode value) {
      if (value == null || value.isNull()) {
         return NULL;
      } else if (value.isObject()) {
         return OBJECT;
      } else if (value.isArray()) {
         return ARRAY;
      } else if (value.isTextual()) {
         return STRING;
      } else if (value.isNumber()) {
         return NUMBER;
      } else if (value.isBoolean()) {
         return BOOLEAN;
      } else {
         return value.isBinary() ? BINARY : OTHER;
      }
   }
}

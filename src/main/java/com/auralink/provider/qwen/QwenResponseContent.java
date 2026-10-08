package com.auralink.provider.qwen;

import java.util.Objects;

record QwenResponseContent(String content, QwenResponseShapeDiagnostic responseShape) {
   QwenResponseContent {
      content = Objects.requireNonNull(content, "content");
      responseShape = Objects.requireNonNull(responseShape, "responseShape");
   }

   @Override
   public String toString() {
      return "QwenResponseContent[content=REDACTED,responseShape=" + this.responseShape + "]";
   }
}

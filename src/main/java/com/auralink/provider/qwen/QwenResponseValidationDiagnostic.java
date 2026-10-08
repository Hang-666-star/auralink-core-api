package com.auralink.provider.qwen;

import com.auralink.creation.provider.ProviderSafeDiagnostic;
import java.util.Objects;

public record QwenResponseValidationDiagnostic(
   QwenResponseValidationStage validationStage, QwenResponseValidationCode validationCode, QwenResponseShapeDiagnostic responseShape
) implements ProviderSafeDiagnostic<QwenResponseValidationStage, QwenResponseValidationCode, QwenResponseShapeDiagnostic> {
   public QwenResponseValidationDiagnostic {
      validationStage = Objects.requireNonNull(validationStage, "validationStage");
      validationCode = Objects.requireNonNull(validationCode, "validationCode");
      responseShape = Objects.requireNonNull(responseShape, "responseShape");
   }
}

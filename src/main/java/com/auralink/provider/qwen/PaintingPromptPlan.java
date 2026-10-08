package com.auralink.provider.qwen;

public record PaintingPromptPlan(
   String schemaVersion, String subject, String scene, String composition, String colorPalette, String brushwork, String artisticConception, String finalPrompt
) {
}

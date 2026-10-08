package com.auralink.guide.provider;

import com.auralink.guide.context.PaintingGuideContext;

public interface GuideProvider {
   GuideGenerationResult generate(String requestId, PaintingGuideContext context);
}

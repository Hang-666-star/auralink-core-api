package com.auralink.guide.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder({"schemaVersion", "summary", "sections", "highlights", "knowledgeReferences"})
public record GuideResult(
   String schemaVersion, String summary, GuideSections sections, List<String> highlights, List<GuideKnowledgeReference> knowledgeReferences
) {
}

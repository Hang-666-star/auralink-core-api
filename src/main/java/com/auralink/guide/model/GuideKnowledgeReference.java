package com.auralink.guide.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"sourceId", "sourceType", "title"})
public record GuideKnowledgeReference(String sourceId, String sourceType, String title) {
}

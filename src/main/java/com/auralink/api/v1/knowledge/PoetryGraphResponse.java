package com.auralink.api.v1.knowledge;

import java.util.List;

/** A validated existing export, not a PostgreSQL graph or newly generated knowledge. */
public record PoetryGraphResponse(
   int schemaVersion, String source, String graphSha256, String statsSha256,
   int nodeCount, int edgeCount, List<Node> nodes, List<Link> links,
   List<Count> entityTypes, List<Count> relationTypes, List<String> sourceLimits
) {
   public record Node(String id, String name, String category, double size, String description) {}
   public record Link(String source, String target, double value, String label, String summary, String detail) {}
   public record Count(String name, long value) {}
}

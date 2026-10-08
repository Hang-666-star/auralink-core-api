package com.auralink.guide.knowledge;

import com.auralink.config.properties.GuideProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StaticKnowledgeLoader {
   static final long MAX_GRAPH_BYTES = 1048576L;
   static final long MAX_STATS_BYTES = 131072L;
   private static final int MAX_NODES = 10000;
   private static final int MAX_LINKS = 20000;
   private static final Set<String> GRAPH_FIELDS = Set.of("nodes", "links");
   private static final Set<String> NODE_FIELDS = Set.of("id", "name", "category", "size", "description", "tooltip");
   private static final Set<String> LINK_FIELDS = Set.of("source", "target", "value", "label", "summary", "detail");
   private static final Set<String> STATS_FIELDS = Set.of("overview", "entityTypeDistribution", "relationTypeDistribution");
   private static final Set<String> OVERVIEW_FIELDS = Set.of("entities", "relations", "poems", "poets");
   private final GuideProperties properties;
   private final ObjectMapper objectMapper;
   private final Path configurationBase;
   private final Path allowedKnowledgeRoot;
   private volatile StaticKnowledgeLoader.LoadedKnowledge cached;

   @Autowired
   public StaticKnowledgeLoader(GuideProperties properties, ObjectMapper objectMapper) {
      this(properties, objectMapper, detectConfigurationBase(), configuredKnowledgeRoot(properties, detectConfigurationBase()));
   }

   StaticKnowledgeLoader(GuideProperties properties, ObjectMapper objectMapper, Path configurationBase, Path allowedKnowledgeRoot) {
      this.properties = properties;
      this.objectMapper = objectMapper;
      this.configurationBase = configurationBase.toAbsolutePath().normalize();
      this.allowedKnowledgeRoot = allowedKnowledgeRoot.toAbsolutePath().normalize();
   }

   StaticKnowledgeLoader.LoadedKnowledge load() {
      StaticKnowledgeLoader.LoadedKnowledge current = this.cached;
      if (current != null) {
         return current;
      }

      synchronized (this) {
         if (this.cached == null) {
            this.cached = this.loadFiles();
         }

         return this.cached;
      }
   }

   private StaticKnowledgeLoader.LoadedKnowledge loadFiles() {
      try {
         Path graphPath = this.resolveKnowledgeFile(this.properties.getPoetryGraphPath());
         Path statsPath = this.resolveKnowledgeFile(this.properties.getPoetryStatsPath());
         byte[] graphBytes = this.readBounded(graphPath, 1048576L);
         byte[] statsBytes = this.readBounded(statsPath, 131072L);
         JsonNode graph = this.objectMapper.readTree(graphBytes);
         JsonNode stats = this.objectMapper.readTree(statsBytes);
         List<StaticKnowledgeLoader.KnowledgeNode> nodes = this.validateGraph(graph);
         this.validateStats(stats, nodes.size(), graph.path("links").size());
         Map<String, String> fingerprints = new LinkedHashMap<>();
         fingerprints.put("poetryGraphSha256", this.sha256(graphBytes));
         fingerprints.put("poetryStatsSha256", this.sha256(statsBytes));
         return new StaticKnowledgeLoader.LoadedKnowledge(nodes, fingerprints);
      } catch (IOException | RuntimeException exception) {
         if (exception instanceof StaticKnowledgeLoader.KnowledgeLoadingException knowledgeLoadingException) {
            throw knowledgeLoadingException;
         } else {
            throw new StaticKnowledgeLoader.KnowledgeLoadingException("Configured guide knowledge could not be loaded", exception);
         }
      }
   }

   private Path resolveKnowledgeFile(String configuredPath) throws IOException {
      if (configuredPath != null && !configuredPath.isBlank()) {
         Path rootReal = this.allowedKnowledgeRoot.toRealPath();
         Path raw = Path.of(configuredPath.strip());
         Path candidate = raw.isAbsolute() ? raw.normalize() : this.configurationBase.resolve(raw).normalize();
         if (candidate.startsWith(this.allowedKnowledgeRoot) && !Files.isSymbolicLink(candidate) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path real = candidate.toRealPath();
            if (real.startsWith(rootReal) && Files.isRegularFile(real)) {
               return real;
            } else {
               throw new StaticKnowledgeLoader.KnowledgeLoadingException("Guide knowledge path is outside the approved data directory");
            }
         } else {
            throw new StaticKnowledgeLoader.KnowledgeLoadingException("Guide knowledge path is outside the approved data directory");
         }
      } else {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Guide knowledge path is not configured");
      }
   }

   private byte[] readBounded(Path path, long maximumBytes) throws IOException {
      if (Files.size(path) > maximumBytes) {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Guide knowledge file exceeds its size limit");
      }

      try (
         InputStream input = Files.newInputStream(path);
         ByteArrayOutputStream output = new ByteArrayOutputStream();
      ) {
         byte[] buffer = new byte[8192];
         long total = 0L;

         int count;
         while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) {
               throw new StaticKnowledgeLoader.KnowledgeLoadingException("Guide knowledge file exceeds its size limit");
            }

            output.write(buffer, 0, count);
         }

         return output.toByteArray();
      }
   }

   private List<StaticKnowledgeLoader.KnowledgeNode> validateGraph(JsonNode graph) {
      this.requireObjectWithFields(graph, GRAPH_FIELDS, "poetry graph");
      JsonNode nodesNode = graph.get("nodes");
      JsonNode linksNode = graph.get("links");
      if (nodesNode.isArray() && linksNode.isArray() && nodesNode.size() <= 10000 && linksNode.size() <= 20000) {
         List<StaticKnowledgeLoader.KnowledgeNode> nodes = new ArrayList<>(nodesNode.size());
         Set<String> nodeIds = new HashSet<>();

         for (JsonNode node : nodesNode) {
            this.requireObjectWithFields(node, NODE_FIELDS, "poetry graph node");
            String id = this.requiredText(node, "id");
            String name = this.requiredText(node, "name");
            String category = this.requiredText(node, "category");
            String description = this.requiredText(node, "description");
            this.requiredText(node, "tooltip");
            if (!node.get("size").isNumber() || !nodeIds.add(id)) {
               throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry graph contains an invalid node");
            }

            nodes.add(new StaticKnowledgeLoader.KnowledgeNode(id, name, category, this.normalizeDescription(description)));
         }

         for (JsonNode link : linksNode) {
            this.requireObjectWithFields(link, LINK_FIELDS, "poetry graph link");
            String source = this.requiredText(link, "source");
            String target = this.requiredText(link, "target");
            this.requiredText(link, "label");
            this.requiredText(link, "summary");
            this.requiredText(link, "detail");
            if (!link.get("value").isNumber() || !nodeIds.contains(source) || !nodeIds.contains(target)) {
               throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry graph contains an invalid link");
            }
         }

         return List.copyOf(nodes);
      } else {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry graph has an invalid structure");
      }
   }

   private void validateStats(JsonNode stats, int nodeCount, int linkCount) {
      this.requireObjectWithFields(stats, STATS_FIELDS, "poetry statistics");
      JsonNode overview = stats.get("overview");
      this.requireObjectWithFields(overview, OVERVIEW_FIELDS, "poetry statistics overview");

      for (String field : OVERVIEW_FIELDS) {
         if (!overview.get(field).canConvertToInt() || overview.get(field).intValue() < 0) {
            throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry statistics overview is invalid");
         }
      }

      if (overview.get("entities").intValue() == nodeCount && overview.get("relations").intValue() == linkCount) {
         this.validateDistribution(stats.get("entityTypeDistribution"));
         this.validateDistribution(stats.get("relationTypeDistribution"));
      } else {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry statistics do not match the graph");
      }
   }

   private void validateDistribution(JsonNode distribution) {
      if (distribution.isArray() && distribution.size() <= 20000) {
         for (JsonNode entry : distribution) {
            if (!entry.isArray()
               || entry.size() != 2
               || !entry.get(0).isTextual()
               || entry.get(0).textValue().isBlank()
               || !entry.get(1).canConvertToInt()
               || entry.get(1).intValue() < 0) {
               throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry statistics distribution is invalid");
            }
         }
      } else {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Poetry statistics distribution is invalid");
      }
   }

   private void requireObjectWithFields(JsonNode node, Set<String> fields, String description) {
      if (node != null && node.isObject()) {
         Set<String> actual = new HashSet<>();
         Iterator<String> names = node.fieldNames();
         names.forEachRemaining(actual::add);
         if (!actual.equals(fields)) {
            throw new StaticKnowledgeLoader.KnowledgeLoadingException("Invalid " + description + " structure");
         }
      } else {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Invalid " + description + " structure");
      }
   }

   private String requiredText(JsonNode node, String field) {
      JsonNode value = node.get(field);
      if (value != null && value.isTextual() && !value.textValue().isBlank()) {
         return value.textValue().strip();
      } else {
         throw new StaticKnowledgeLoader.KnowledgeLoadingException("Guide knowledge contains a missing text field");
      }
   }

   private String normalizeDescription(String description) {
      return description.replace("<SEP>", "\n").strip();
   }

   private String sha256(byte[] content) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
      } catch (NoSuchAlgorithmException exception) {
         throw new IllegalStateException("SHA-256 is unavailable", exception);
      }
   }

   private static Path detectConfigurationBase() {
      Path workingDirectory = Path.of("").toAbsolutePath().normalize();
      return Files.isDirectory(workingDirectory.resolve("backend")) && Files.isDirectory(workingDirectory.resolve("frontend/public/data"))
         ? workingDirectory.resolve("backend")
         : workingDirectory;
   }

   private static Path detectKnowledgeRoot() {
      Path workingDirectory = Path.of("").toAbsolutePath().normalize();
      if (Files.isDirectory(workingDirectory.resolve("frontend/public/data"))) {
         return workingDirectory.resolve("frontend/public/data");
      }

      Path parent = workingDirectory.getParent();
      return parent != null && Files.isDirectory(parent.resolve("frontend/public/data"))
         ? parent.resolve("frontend/public/data")
         : workingDirectory.resolve("frontend/public/data");
   }

   static Path configuredKnowledgeRoot(GuideProperties properties, Path configurationBase) {
      String configured = properties.getKnowledgeRoot();
      if (configured != null && !configured.isBlank()) {
         Path raw = Path.of(configured.strip());
         return (raw.isAbsolute() ? raw : configurationBase.resolve(raw)).normalize();
      } else {
         return detectKnowledgeRoot();
      }
   }

   public static class KnowledgeLoadingException extends IllegalStateException {
      public KnowledgeLoadingException(String message) {
         super(message);
      }

      public KnowledgeLoadingException(String message, Throwable cause) {
         super(message, cause);
      }
   }

   record KnowledgeNode(String id, String name, String category, String description) {
   }

   record LoadedKnowledge(List<StaticKnowledgeLoader.KnowledgeNode> nodes, Map<String, String> fingerprints) {
      LoadedKnowledge {
         nodes = List.copyOf(nodes);
         fingerprints = Collections.unmodifiableMap(new TreeMap<>(fingerprints));
      }
   }
}

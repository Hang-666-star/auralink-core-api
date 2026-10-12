package com.auralink.service.knowledge;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.api.v1.knowledge.ChinaMapResponse;
import com.auralink.api.v1.knowledge.PoetryGraphResponse;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Reads only three fixed local filenames. Never downloads, writes, or calls a model. */
@Service
public class KnowledgeSnapshotService {
   private static final int MAX_BYTES = 8 * 1024 * 1024;
   private final ObjectMapper mapper;
   private final String root;
   private final Map<String, String> expectedHashes;

   public KnowledgeSnapshotService(ObjectMapper mapper,
      @Value("${auralink.knowledge.snapshot-root:${AURALINK_KNOWLEDGE_SNAPSHOT_ROOT:}}") String root,
      @Value("${auralink.knowledge.graph-sha256:${AURALINK_KNOWLEDGE_GRAPH_SHA256:}}") String graphHash,
      @Value("${auralink.knowledge.stats-sha256:${AURALINK_KNOWLEDGE_STATS_SHA256:}}") String statsHash,
      @Value("${auralink.knowledge.china-sha256:${AURALINK_KNOWLEDGE_CHINA_SHA256:}}") String chinaHash) {
      this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
         .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
      this.root = root;
      this.expectedHashes = Map.of("poetry-graph.json", graphHash, "poetry-stats.json", statsHash, "china.json", chinaHash);
   }

   public PoetryGraphResponse graph() {
      try {
         Artifact graph = read("poetry-graph.json");
         Artifact stats = read("poetry-stats.json");
         JsonNode nodeData = array(graph.json(), "nodes", 2000);
         JsonNode linkData = array(graph.json(), "links", 10000);
         Set<String> ids = new HashSet<>();
         List<PoetryGraphResponse.Node> nodes = new ArrayList<>();
         Map<String, Long> types = new LinkedHashMap<>();
         for (JsonNode n : nodeData) {
            String id = text(n, "id", 512, false);
            require(ids.add(id));
            String category = text(n, "category", 512, false);
            nodes.add(new PoetryGraphResponse.Node(id, text(n, "name", 2000, false), category,
               number(n, "size", 1, 400), text(n, "description", 32000, true)));
            types.merge(category, 1L, Long::sum);
         }
         List<PoetryGraphResponse.Link> links = new ArrayList<>();
         Map<String, Long> relations = new LinkedHashMap<>();
         for (JsonNode e : linkData) {
            String source = text(e, "source", 512, false);
            String target = text(e, "target", 512, false);
            require(ids.contains(source) && ids.contains(target));
            String label = text(e, "label", 4000, true);
            links.add(new PoetryGraphResponse.Link(source, target, number(e, "value", 0, 1000000),
               label, text(e, "summary", 4000, true), text(e, "detail", 32000, true)));
            // Match the legacy exporter literally. No translation or edge rewriting.
            String first = label.isEmpty() ? "其他" : label.split(",", -1)[0];
            relations.merge(first, 1L, Long::sum);
         }
         JsonNode overview = stats.json().path("overview");
         require(count(overview, "entities") == nodes.size() && count(overview, "relations") == links.size());
         require(count(overview, "poems") == nodes.stream().filter(n -> n.category().contains("诗词")).count());
         require(count(overview, "poets") == nodes.stream().filter(n -> n.category().contains("诗人")).count());
         require(distribution(stats.json(), "entityTypeDistribution").equals(types));
         require(distribution(stats.json(), "relationTypeDistribution").equals(relations));
         return new PoetryGraphResponse(1, "legacy-poetry-snapshot", graph.sha256(), stats.sha256(),
            nodes.size(), links.size(), List.copyOf(nodes), List.copyOf(links), counts(types), counts(relations), List.of(
               "现有诗词图谱文件快照，未存入本功能的 PostgreSQL 图谱表。",
               "原始 GraphML 未找到，上游提取准确性未经独立验证；保留原有标签与关系。",
               "图谱不会自动包含新增绘画记录；类别统计不等于已核实的诗作或人物数量。"));
      } catch (IOException | IllegalArgumentException exception) {
         throw unavailable("诗词图谱快照不可用：文件缺失、完整性不符或格式校验失败，请联系管理员检查快照配置。");
      }
   }

   public ChinaMapResponse china() {
      try {
         Artifact map = read("china.json");
         require("FeatureCollection".equals(map.json().path("type").asText()));
         List<ChinaMapResponse.Feature> features = new ArrayList<>();
         Set<String> names = new HashSet<>();
         for (JsonNode f : array(map.json(), "features", 100)) {
            require("Feature".equals(f.path("type").asText()));
            // The inherited map contains one unnamed boundary feature; preserve it.
            String name = text(f.path("properties"), "name", 100, true);
            require(names.add(name));
            JsonNode geometry = f.path("geometry");
            String type = geometry.path("type").asText();
            require("Polygon".equals(type) || "MultiPolygon".equals(type));
            JsonNode coordinates = geometry.path("coordinates");
            validateCoordinates(coordinates, "Polygon".equals(type) ? 2 : 3);
            features.add(new ChinaMapResponse.Feature("Feature", new ChinaMapResponse.Properties(name),
               new ChinaMapResponse.Geometry(type, coordinates)));
         }
         require(!features.isEmpty());
         return new ChinaMapResponse("FeatureCollection", List.copyOf(features), map.sha256());
      } catch (IOException | IllegalArgumentException exception) {
         throw unavailable("地理底图不可用：china.json 缺失、完整性不符或格式校验失败。地域统计仍可读取。");
      }
   }

   private Artifact read(String filename) throws IOException {
      if (root == null || root.isBlank()) throw unavailable("知识快照尚未配置，请管理员设置快照目录。");
      Path directory = Path.of(root).toRealPath();
      Path file = directory.resolve(filename).toRealPath();
      require(file.getParent().equals(directory) && Files.isRegularFile(file));
      require(Files.size(file) <= MAX_BYTES);
      byte[] bytes;
      try (var stream = Files.newInputStream(file)) { bytes = stream.readNBytes(MAX_BYTES + 1); }
      require(bytes.length <= MAX_BYTES);
      String hash;
      try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
      catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
      String expected = expectedHashes.get(filename);
      require(expected == null || expected.isBlank() || hash.equalsIgnoreCase(expected));
      JsonNode json = mapper.readTree(bytes);
      require(json != null && json.isObject());
      return new Artifact(json, hash);
   }

   private static void validateCoordinates(JsonNode n, int depth) {
      require(n.isArray() && !n.isEmpty());
      if (depth == 0) {
         require(n.size() == 2 && n.get(0).isNumber() && n.get(1).isNumber());
         double x = n.get(0).doubleValue(), y = n.get(1).doubleValue();
         require(Double.isFinite(x) && Double.isFinite(y) && Math.abs(x) <= 180 && Math.abs(y) <= 90);
      } else {
         for (JsonNode c : n) validateCoordinates(c, depth - 1);
         if (depth == 1) {
            // GeoJSON linear rings contain at least four positions, including
            // a closing position numerically equal to the first one.
            require(n.size() >= 4);
            JsonNode first = n.get(0), last = n.get(n.size() - 1);
            require(first.get(0).decimalValue().compareTo(last.get(0).decimalValue()) == 0
               && first.get(1).decimalValue().compareTo(last.get(1).decimalValue()) == 0);
         }
      }
   }

   private static JsonNode array(JsonNode n, String key, int max) {
      JsonNode a = n.path(key);
      require(a.isArray() && a.size() <= max);
      return a;
   }
   private static String text(JsonNode n, String key, int max, boolean empty) {
      JsonNode v = n.path(key);
      require(v.isTextual() && v.textValue().length() <= max && (empty || !v.textValue().isBlank()));
      return v.textValue();
   }
   private static double number(JsonNode n, String key, double min, double max) {
      JsonNode v = n.path(key);
      double d = v.doubleValue();
      require(v.isNumber() && Double.isFinite(d) && d >= min && d <= max);
      return d;
   }
   private static long count(JsonNode n, String key) {
      JsonNode v = n.path(key);
      require(v.isIntegralNumber() && v.canConvertToLong() && v.longValue() >= 0);
      return v.longValue();
   }
   private static Map<String, Long> distribution(JsonNode n, String key) {
      Map<String, Long> result = new LinkedHashMap<>();
      for (JsonNode pair : array(n, key, 10000)) {
         require(pair.isArray() && pair.size() == 2 && pair.get(0).isTextual()
            && pair.get(1).isIntegralNumber() && pair.get(1).canConvertToLong() && pair.get(1).longValue() > 0);
         require(result.putIfAbsent(pair.get(0).textValue(), pair.get(1).longValue()) == null);
      }
      return result;
   }
   private static List<PoetryGraphResponse.Count> counts(Map<String, Long> map) {
      return map.entrySet().stream().map(e -> new PoetryGraphResponse.Count(e.getKey(), e.getValue()))
         .sorted(java.util.Comparator.comparingLong(PoetryGraphResponse.Count::value).reversed()
            .thenComparing(PoetryGraphResponse.Count::name)).toList();
   }
   private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException("Invalid snapshot"); }
   private static ApiV1Exception unavailable(String message) {
      return new ApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.KNOWLEDGE_SNAPSHOT_UNAVAILABLE, message);
   }
   private record Artifact(JsonNode json, String sha256) {}
}

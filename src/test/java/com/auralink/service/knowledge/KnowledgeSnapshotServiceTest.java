package com.auralink.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.auralink.api.v1.error.ApiV1Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeSnapshotServiceTest {
   @TempDir Path directory;
   private final ObjectMapper mapper = new ObjectMapper();
   private static final String GRAPH = """
      {"nodes":[
        {"id":"a","name":"<img src=x onerror=alert(1)>","category":"人物/诗人","size":20,"description":"synthetic only"},
        {"id":"b","name":"测试诗","category":"事件/诗词","size":20,"description":"test"}],
       "links":[{"source":"a","target":"b","value":1,"label":"作者,测试","summary":"<script>test</script>","detail":"original uncertain label"}]}
      """;
   private static final String STATS = """
      {"overview":{"entities":2,"relations":1,"poems":1,"poets":1},
       "entityTypeDistribution":[["人物/诗人",1],["事件/诗词",1]],"relationTypeDistribution":[["作者",1]]}
      """;

   @BeforeEach void files() throws Exception {
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH);
      Files.writeString(directory.resolve("poetry-stats.json"), STATS);
      Files.writeString(directory.resolve("china.json"), """
         {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":"测试省"},
          "geometry":{"type":"Polygon","coordinates":[[[110,30],[111,30],[111,31],[110,30]]]}}]}
         """);
   }
   private String hash(String filename) {
      try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(filename)))); }
      catch (Exception exception) { throw new IllegalStateException(exception); }
   }
   private KnowledgeSnapshotService service() { return service(true); }
   private KnowledgeSnapshotService service(boolean mapEnabled) {
      return new KnowledgeSnapshotService(mapper, directory.toString(), hash("poetry-graph.json"),
         Files.exists(directory.resolve("poetry-stats.json")) ? hash("poetry-stats.json") : "0".repeat(64),
         mapEnabled ? hash("china.json") : "", mapEnabled);
   }

   @Test void validatesSnapshotAndPreservesUntrustedTextAndOriginalRelations() {
      var graph = service().graph();
      assertEquals(2, graph.nodeCount());
      assertEquals(1, graph.edgeCount());
      assertEquals(64, graph.graphSha256().length());
      assertEquals("<img src=x onerror=alert(1)>", graph.nodes().get(0).name());
      assertEquals("作者,测试", graph.links().get(0).label());
      assertEquals("作者", graph.relationTypes().get(0).name());
      assertEquals("legacy-poetry-snapshot", graph.source());
   }
   @Test void rejectsDuplicateIdsDanglingEdgesAndInconsistentStatistics() throws Exception {
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH.replace("\"id\":\"b\"", "\"id\":\"a\""));
      assertThrows(ApiV1Exception.class, () -> service().graph());
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH.replace("\"target\":\"b\"", "\"target\":\"missing\""));
      assertThrows(ApiV1Exception.class, () -> service().graph());
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH);
      Files.writeString(directory.resolve("poetry-stats.json"), STATS.replace("\"relations\":1", "\"relations\":2"));
      assertThrows(ApiV1Exception.class, () -> service().graph());
   }
   @Test void rejectsDuplicateJsonKeysAndTrailingDocuments() throws Exception {
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH.replace("\"size\":20", "\"size\":20,\"size\":30"));
      assertThrows(ApiV1Exception.class, () -> service().graph());
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH + "{}");
      assertThrows(ApiV1Exception.class, () -> service().graph());
   }
   @Test void missingFilesReturn503WithoutPathAndCanBeRetried() throws Exception {
      Files.delete(directory.resolve("poetry-stats.json"));
      ApiV1Exception error = assertThrows(ApiV1Exception.class, () -> service().graph());
      assertEquals(503, error.getStatus().value());
      assertFalse(error.getMessage().contains(directory.toString()));
      Files.writeString(directory.resolve("poetry-stats.json"), STATS);
      assertEquals(2, service().graph().nodeCount());
   }
   @Test void hashBindingAndSymlinkEscapeFailClosed() throws Exception {
      var badHash = new KnowledgeSnapshotService(mapper, directory.toString(), "0".repeat(64), hash("poetry-stats.json"), "", false);
      assertThrows(ApiV1Exception.class, badHash::graph);
      Path sibling = directory.resolveSibling(directory.getFileName() + "-outside.json");
      Files.writeString(sibling, STATS);
      try {
         Files.delete(directory.resolve("poetry-stats.json"));
         Files.createSymbolicLink(directory.resolve("poetry-stats.json"), sibling);
         assertThrows(ApiV1Exception.class, () -> service().graph());
      } finally { Files.deleteIfExists(sibling); }
   }
   @Test void missingConfigurationDoesNotPreventOtherApplicationFeatures() {
      var missing = new KnowledgeSnapshotService(mapper, "", "", "", "", false);
      assertEquals(503, assertThrows(ApiV1Exception.class, missing::graph).getStatus().value());
   }
   @Test void emptyGraphIsAValidSnapshotAndMapValidationIsIndependent() throws Exception {
      Files.writeString(directory.resolve("poetry-graph.json"), "{\"nodes\":[],\"links\":[]}");
      Files.writeString(directory.resolve("poetry-stats.json"), """
         {"overview":{"entities":0,"relations":0,"poems":0,"poets":0},"entityTypeDistribution":[],"relationTypeDistribution":[]}
         """);
      assertEquals(0, service().graph().nodeCount());
      assertEquals(1, service().china().features().size());
      Files.writeString(directory.resolve("china.json"), Files.readString(directory.resolve("china.json")).replace("[110,30]", "[999,30]"));
      assertThrows(ApiV1Exception.class, () -> service().china());
   }

   @Test void rejectsShortAndUnclosedRingsInPolygonsAndMultiPolygons() throws Exception {
      for (String type : List.of("Polygon", "MultiPolygon")) {
         for (String ring : List.of("[[110,30]]", "[[110,30],[111,30],[111,31],[112,31]]")) {
            writeMap(type, ring);
            ApiV1Exception error = assertThrows(ApiV1Exception.class, () -> service().china(), type + ": " + ring);
            assertEquals(503, error.getStatus().value());
            assertEquals("KNOWLEDGE_SNAPSHOT_UNAVAILABLE", error.getCode().name());
            assertFalse(error.getMessage().contains(directory.toString()));
         }
      }
   }

   @Test void acceptsClosedRingsWithNumericallyEquivalentCoordinateRepresentations() throws Exception {
      for (String type : List.of("Polygon", "MultiPolygon")) {
         writeMap(type, "[[110,30],[111,30],[111,31],[110.0,3e1]]");
         var map = service().china();
         assertEquals(1, map.features().size());
         assertEquals(type, map.features().get(0).geometry().type());
      }
   }

   @Test void noMapModeNeverLoadsMapEvenWithOldValidFileAndBinding() throws Exception {
      var disabled = new KnowledgeSnapshotService(mapper, directory.toString(), hash("poetry-graph.json"),
         hash("poetry-stats.json"), hash("china.json"), false);
      for (int attempt = 0; attempt < 3; attempt++) {
         ApiV1Exception error = assertThrows(ApiV1Exception.class, disabled::china);
         assertEquals(503, error.getStatus().value());
         assertEquals("KNOWLEDGE_SNAPSHOT_UNAVAILABLE", error.getCode().name());
         assertEquals("此版本未启用地理底图；地域统计仍可读取。", error.getMessage());
         assertFalse(error.getMessage().contains(directory.toString()));
         assertEquals(2, disabled.graph().nodeCount());
         if (attempt == 0) Files.writeString(directory.resolve("china.json"), "not JSON");
         else Files.deleteIfExists(directory.resolve("china.json"));
      }
   }

   @Test void poetryOnlyNeedsItsTwoFilesAndRequiresBothHashBindings() throws Exception {
      Files.delete(directory.resolve("china.json"));
      assertEquals(2, service(false).graph().nodeCount());
      String graphHash = hash("poetry-graph.json"), statsHash = hash("poetry-stats.json");
      for (String invalid : List.of("", "invalid", "0".repeat(64))) {
         assertThrows(ApiV1Exception.class, () -> new KnowledgeSnapshotService(mapper, directory.toString(), invalid, statsHash, "", false).graph());
         assertThrows(ApiV1Exception.class, () -> new KnowledgeSnapshotService(mapper, directory.toString(), graphHash, invalid, "", false).graph());
      }
      var bound = service(false);
      Files.writeString(directory.resolve("poetry-stats.json"), "invalid JSON");
      assertEquals(503, assertThrows(ApiV1Exception.class, bound::graph).getStatus().value());
      Files.writeString(directory.resolve("poetry-stats.json"), STATS);
      assertEquals(2, bound.graph().nodeCount());
   }

   @Test void eitherRequiredPoetryFileCanFailAndRecoverWithoutChangingTheBinding() throws Exception {
      var bound = service(false);
      for (String filename : List.of("poetry-graph.json", "poetry-stats.json")) {
         Path file = directory.resolve(filename);
         byte[] original = Files.readAllBytes(file);
         Files.delete(file);
         assertEquals(503, assertThrows(ApiV1Exception.class, bound::graph).getStatus().value());
         Files.write(file, original);
         assertEquals(2, bound.graph().nodeCount());
         Files.writeString(file, "not the approved snapshot");
         assertEquals(503, assertThrows(ApiV1Exception.class, bound::graph).getStatus().value());
         Files.write(file, original);
         assertEquals(2, bound.graph().nodeCount());
      }
   }

   @Test void mapEnabledStillRequiresItsOwnHashAndValidAsset() {
      var enabled = new KnowledgeSnapshotService(mapper, directory.toString(), hash("poetry-graph.json"), hash("poetry-stats.json"), "", true);
      assertEquals(503, assertThrows(ApiV1Exception.class, enabled::china).getStatus().value());
      assertEquals(2, enabled.graph().nodeCount());
   }

   private void writeMap(String type, String ring) throws Exception {
      String coordinates = "[" + ring + "]";
      if ("MultiPolygon".equals(type)) coordinates = "[" + coordinates + "]";
      Files.writeString(directory.resolve("china.json"), """
         {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":"测试省"},
          "geometry":{"type":"%s","coordinates":%s}}]}
         """.formatted(type, coordinates));
   }
}

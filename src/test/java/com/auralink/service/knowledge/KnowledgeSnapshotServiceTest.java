package com.auralink.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.auralink.api.v1.error.ApiV1Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
   private KnowledgeSnapshotService service() { return new KnowledgeSnapshotService(mapper, directory.toString(), "", "", ""); }

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
      var badHash = new KnowledgeSnapshotService(mapper, directory.toString(), "0".repeat(64), "", "");
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
      var missing = new KnowledgeSnapshotService(mapper, "", "", "", "");
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

   private void writeMap(String type, String ring) throws Exception {
      String coordinates = "[" + ring + "]";
      if ("MultiPolygon".equals(type)) coordinates = "[" + coordinates + "]";
      Files.writeString(directory.resolve("china.json"), """
         {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":"测试省"},
          "geometry":{"type":"%s","coordinates":%s}}]}
         """.formatted(type, coordinates));
   }
}

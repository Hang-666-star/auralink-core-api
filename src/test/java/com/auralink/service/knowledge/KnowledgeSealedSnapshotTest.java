package com.auralink.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import com.auralink.api.v1.error.ApiV1Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Linux transport integration: a real Python launcher seals memfds and execs Java 17. */
@EnabledOnOs(OS.LINUX)
class KnowledgeSealedSnapshotTest {
   @TempDir Path directory;
   private static final String GRAPH = """
      {"nodes":[{"id":"test","name":"合成诗人","category":"诗人","size":20,"description":"fixture"}],"links":[]}
      """;
   private static final String STATS = """
      {"overview":{"entities":1,"relations":0,"poems":0,"poets":1},
       "entityTypeDistribution":[["诗人",1]],"relationTypeDistribution":[]}
      """;

   private static final String LAUNCHER = """
      import fcntl, os, pathlib, sys
      java, cp, probe, root, graph_hash, stats_hash, mode = sys.argv[1:]
      for number, name in ((198, 'poetry-graph.json'), (199, 'poetry-stats.json')):
          if mode == 'missing' or (mode == 'missing-stats' and number == 199):
              continue
          if mode == 'linked-file':
              fd = os.open(pathlib.Path(root) / name, os.O_RDONLY)
          else:
              fd = os.memfd_create('artlive-test-' + name, os.MFD_ALLOW_SEALING | os.MFD_CLOEXEC)
              data = (pathlib.Path(root) / name).read_bytes()
              if mode == 'corrupt' and number == 198:
                  data = b'{bad json'
              os.write(fd, data)
              required = fcntl.F_SEAL_SEAL | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_GROW | fcntl.F_SEAL_WRITE
              fcntl.fcntl(fd, fcntl.F_ADD_SEALS, required)
              assert fcntl.fcntl(fd, fcntl.F_GET_SEALS) == required
              # Prove immutability at the source; Core does not claim to call F_GET_SEALS.
              try:
                  os.pwrite(fd, b'x', 0)
                  raise AssertionError('sealed write accepted')
              except PermissionError:
                  pass
          os.dup2(fd, number, inheritable=True)
          os.close(fd)
      os.execv(java, [java, '-cp', cp, probe, root, graph_hash, stats_hash, mode])
      """;

   @Test void sealedDescriptorsSurviveActualExecAndDoNotNeedLiveDirectory() throws Exception {
      runProbe("valid");
   }

   @Test void refusesMissingDescriptorsWithoutFallingBackToValidDurableFiles() throws Exception {
      runProbe("missing");
      runProbe("missing-stats");
   }

   @Test void refusesWrongHashCorruptionAndOrdinaryLinkedFiles() throws Exception {
      runProbe("wrong-hash");
      runProbe("corrupt");
      runProbe("linked-file");
   }

   @Test void refusesMapEnabledAndUnknownTransportRatherThanSelectingFilesystem() throws Exception {
      runProbe("map-enabled");
      runProbe("unknown-transport");
   }

   private void runProbe(String mode) throws Exception {
      Files.writeString(directory.resolve("poetry-graph.json"), GRAPH);
      Files.writeString(directory.resolve("poetry-stats.json"), STATS);
      String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
      Process process = new ProcessBuilder("python3", "-c", LAUNCHER,
         Path.of(System.getProperty("java.home"), "bin", "java").toString(), cp,
         Probe.class.getName(), directory.toString(), hash(GRAPH), hash(STATS), mode)
         .redirectErrorStream(true).start();
      boolean exited = process.waitFor(30, TimeUnit.SECONDS);
      if (!exited) process.destroyForcibly();
      assertTrue(exited, "isolated descriptor probe timed out: " + mode);
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), mode + ": " + output);
      assertTrue(output.contains("PROBE_OK " + mode), output);
   }

   private static String hash(String value) throws Exception {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
   }

   /** Separate JVM entry, intentionally no Spring context, DB, HTTP or provider access. */
   public static class Probe {
      public static void main(String[] args) {
         String mode = args[3];
         boolean valid = "valid".equals(mode);
         // The successful read cannot open this nonexistent directory. Other modes
         // keep valid disk files present to detect an accidental fallback.
         String root = valid ? Path.of(args[0], "not-a-live-directory").toString() : args[0];
         var service = new KnowledgeSnapshotService(new ObjectMapper(), root,
            "wrong-hash".equals(mode) ? "0".repeat(64) : args[1], args[2], "",
            "map-enabled".equals(mode), "unknown-transport".equals(mode) ? "unknown" : "sealed-memfd-v1");
         if (valid) {
            var response = service.graph();
            if (response.nodeCount() != 1 || response.edgeCount() != 0) throw new AssertionError("snapshot counts");
            if (!response.graphSha256().equals(args[1]) || !response.statsSha256().equals(args[2]))
               throw new AssertionError("snapshot hashes");
         } else {
            expectUnavailable(service::graph, root);
         }
         expectUnavailable(service::china, root);
         System.out.println("PROBE_OK " + mode);
      }

      private static void expectUnavailable(Runnable operation, String root) {
         try { operation.run(); throw new AssertionError("unsafe snapshot accepted"); }
         catch (ApiV1Exception error) {
            if (error.getStatus().value() != 503 || !"KNOWLEDGE_SNAPSHOT_UNAVAILABLE".equals(error.getCode().name())
               || error.getMessage().contains(root) || error.getMessage().contains("/proc/"))
               throw new AssertionError("unsafe error response", error);
         }
      }
   }
}

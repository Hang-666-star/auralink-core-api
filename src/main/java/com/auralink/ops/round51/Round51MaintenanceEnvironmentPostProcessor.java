package com.auralink.ops.round51;

import com.auralink.Application;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

public final class Round51MaintenanceEnvironmentPostProcessor implements EnvironmentPostProcessor {
   static final Path SERVER_PROJECT_ROOT = Path.of("/root/autodl-tmp/auralink");
   static final Path SERVER_MARKER = Path.of("/root/auralink_activation_backups/.round51-maintenance");
   static final Path SERVER_STARTUP_GATE = Path.of("/root/auralink_activation_backups/.round51-startup-gate");
   static final Path APPROVED_ISOLATED_PREVIEW_BASE = Path.of("/root/autodl-tmp/artlive/backend-preview");
   static final String ORPHAN_FENCE_GLOB = ".round51-*-startup-gate-orphan-fence-*";
   static final String TOKEN_PROPERTY = "AURALINK_ROUND51_MAINTENANCE_TOKEN";
   static final String ISOLATED_PREVIEW_ENABLED_PROPERTY = "auralink.round51.isolated-preview.enabled";
   static final String ISOLATED_PREVIEW_ROOT_PROPERTY = "auralink.round51.isolated-preview.root";
   static final String ISOLATED_PREVIEW_WORKING_DIRECTORY_PROPERTY = "auralink.round51.isolated-preview.working-directory";
   private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9a-f]{64}");
   private static final int MAX_MARKER_BYTES = 129;
   private final Path marker;
   private final Path startupGate;
   private final Path serverProjectRoot;
   private final Path approvedPreviewBase;
   private final Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchorsSupplier runtimeAnchorsSupplier;
   private final List<Path> injectedProtectedBoundaries;
   private final boolean runtimeClassificationRequired;
   private final boolean enforcementRequired;
   private static final List<Round51MaintenanceEnvironmentPostProcessor.GateLease> HELD_STARTUP_GATES = new CopyOnWriteArrayList<>();
   private static final ThreadLocal<Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths> TEST_PATHS = new ThreadLocal<>();

   public Round51MaintenanceEnvironmentPostProcessor() {
      this(defaultPathsForCurrentThread(), true);
   }

   Round51MaintenanceEnvironmentPostProcessor(Path marker) {
      this(
         marker,
         marker.resolveSibling(".round51-startup-gate"),
         null,
         false,
         true,
         APPROVED_ISOLATED_PREVIEW_BASE,
         Round51MaintenanceEnvironmentPostProcessor::defaultRuntimeAnchors,
         null
      );
   }

   Round51MaintenanceEnvironmentPostProcessor(Path marker, Path startupGate) {
      this(marker, startupGate, null, false, true, APPROVED_ISOLATED_PREVIEW_BASE, Round51MaintenanceEnvironmentPostProcessor::defaultRuntimeAnchors, null);
   }

   static Round51MaintenanceEnvironmentPostProcessor forRuntimeClassificationTest(
      Path marker, Path startupGate, Path serverProjectRoot, Path approvedPreviewBase, Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchors runtimeAnchors
   ) {
      return new Round51MaintenanceEnvironmentPostProcessor(
         marker,
         startupGate,
         serverProjectRoot,
         true,
         true,
         approvedPreviewBase,
         () -> runtimeAnchors,
         List.of(serverProjectRoot, marker, marker.getParent(), startupGate, startupGate.getParent())
      );
   }

   private Round51MaintenanceEnvironmentPostProcessor(Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths paths, boolean runtimeClassificationRequired) {
      this(
         paths.marker(),
         paths.startupGate(),
         paths.serverProjectRoot(),
         runtimeClassificationRequired,
         true,
         APPROVED_ISOLATED_PREVIEW_BASE,
         Round51MaintenanceEnvironmentPostProcessor::defaultRuntimeAnchors,
         null
      );
   }

   private Round51MaintenanceEnvironmentPostProcessor(
      Path marker,
      Path startupGate,
      Path serverProjectRoot,
      boolean runtimeClassificationRequired,
      boolean enforcementRequired,
      Path approvedPreviewBase,
      Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchorsSupplier runtimeAnchorsSupplier,
      List<Path> injectedProtectedBoundaries
   ) {
      this.marker = marker;
      this.startupGate = startupGate;
      this.serverProjectRoot = serverProjectRoot;
      this.approvedPreviewBase = approvedPreviewBase;
      this.runtimeAnchorsSupplier = runtimeAnchorsSupplier;
      this.injectedProtectedBoundaries = injectedProtectedBoundaries;
      this.runtimeClassificationRequired = runtimeClassificationRequired;
      this.enforcementRequired = enforcementRequired;
   }

   static Round51MaintenanceEnvironmentPostProcessor.TestPathScope isolatePathsForCurrentThread(Path marker, Path startupGate, Path offServerProjectRoot) {
      Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths previous = TEST_PATHS.get();
      Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths installed = new Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths(
         marker, startupGate, offServerProjectRoot, false
      );
      TEST_PATHS.set(installed);
      return new Round51MaintenanceEnvironmentPostProcessor.TestPathScope(previous, installed);
   }

   static void installAutomaticPathsForCurrentThread(Path marker, Path startupGate, Path offServerProjectRoot) {
      Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths current = TEST_PATHS.get();
      if (current == null || current.automatic()) {
         TEST_PATHS.set(new Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths(marker, startupGate, offServerProjectRoot, true));
      }
   }

   static void releaseHeldStartupGatesForTests() {
      for (Round51MaintenanceEnvironmentPostProcessor.GateLease lease : List.copyOf(HELD_STARTUP_GATES)) {
         if (HELD_STARTUP_GATES.remove(lease)) {
            lease.close();
         }
      }
   }

   private static Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths defaultPathsForCurrentThread() {
      Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths testPaths = TEST_PATHS.get();
      return testPaths != null
         ? testPaths
         : new Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths(SERVER_MARKER, SERVER_STARTUP_GATE, SERVER_PROJECT_ROOT, false);
   }

   public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
      if (this.enforcementRequired) {
         if (this.runtimeClassificationRequired) {
            Round51MaintenanceEnvironmentPostProcessor.RuntimeMode mode = classifyRuntime(
               environment,
               this.runtimeAnchorsSupplier.resolve(),
               this.serverProjectRoot,
               this.marker.getParent(),
               this.approvedPreviewBase,
               this.injectedProtectedBoundaries == null ? this.protectedPreviewBoundaries() : this.injectedProtectedBoundaries,
               rec$ -> rec$.toRealPath()
            );
            if (mode == Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.VALID_ISOLATED_PREVIEW
               || mode == Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.OFF_SERVER) {
               return;
            }

            if (mode == Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.INVALID_OR_AMBIGUOUS) {
               refuse();
            }
         }

         this.enforceProductionFence(environment);
      }
   }

   private void enforceProductionFence(ConfigurableEnvironment environment) {
      Path parent = this.marker.getParent();
      if (Files.notExists(parent, LinkOption.NOFOLLOW_LINKS)) {
         if (!this.runtimeClassificationRequired) {
            return;
         }

         this.provisionPrivateDirectory(parent);
      }

      if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
         || Files.isSymbolicLink(parent)
         || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
         || !this.hasPrivatePermissions(parent)) {
         refuse();
      }

      if (this.markerExists()) {
         this.requireOwnerToken(environment);
      } else {
         if (this.hasDurableOrphanFence(parent)) {
            refuse();
         }

         this.provisionPrivateGate(this.startupGate);
         if (!Files.exists(this.startupGate, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(this.startupGate)
            || !Files.isRegularFile(this.startupGate, LinkOption.NOFOLLOW_LINKS)) {
            refuse();
         }

         Round51MaintenanceEnvironmentPostProcessor.GateLease lease = this.acquireSharedStartupGate();
         if (this.markerExists()) {
            lease.close();
            this.requireOwnerToken(environment);
         } else {
            HELD_STARTUP_GATES.add(lease);
         }
      }
   }

   static Round51MaintenanceEnvironmentPostProcessor.RuntimeMode classifyRuntime(
      ConfigurableEnvironment environment,
      Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchors suppliedAnchors,
      Path protectedProductionCheckout,
      Path fixedFenceBoundary,
      Path approvedPreviewBase,
      List<Path> additionalProtectedBoundaries,
      Round51MaintenanceEnvironmentPostProcessor.PreviewPathRealizer pathRealizer
   ) {
      String enabled = explicitPreviewProperty(environment, "auralink.round51.isolated-preview.enabled");
      Path production = canonicalProtectedBoundaryOrNull(protectedProductionCheckout, pathRealizer);
      Path fence = canonicalProtectedBoundaryOrNull(fixedFenceBoundary, pathRealizer);
      if (production != null && fence != null && suppliedAnchors != null) {
         Path workingDirectory = canonicalExistingDirectoryOrNull(suppliedAnchors.workingDirectory(), pathRealizer);
         Path applicationSource = canonicalApplicationSourceOrNull(suppliedAnchors.applicationSource(), pathRealizer);
         if ((workingDirectory == null || !isWithinOrEqual(workingDirectory, production))
            && (applicationSource == null || !isWithinOrEqual(applicationSource, production))) {
            if (workingDirectory == null || applicationSource == null) {
               return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.INVALID_OR_AMBIGUOUS;
            }

            if (pathsOverlap(workingDirectory, fence) || pathsOverlap(applicationSource, fence)) {
               return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.INVALID_OR_AMBIGUOUS;
            }

            if (!"true".equals(enabled)) {
               return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.OFF_SERVER;
            }

            Path userHome = canonicalExistingDirectoryOrNull(suppliedAnchors.userHomeDirectory(), pathRealizer);
            Path previewBase = canonicalExistingDirectoryOrNull(approvedPreviewBase, pathRealizer);
            Path previewRoot = canonicalExistingDirectoryOrNull(pathFromExplicitProperty(environment, "auralink.round51.isolated-preview.root"), pathRealizer);
            Path configuredWorkingDirectory = canonicalExistingDirectoryOrNull(
               pathFromExplicitProperty(environment, "auralink.round51.isolated-preview.working-directory"), pathRealizer
            );
            if (userHome != null
               && previewBase != null
               && previewRoot != null
               && configuredWorkingDirectory != null
               && !isFilesystemRoot(previewRoot)
               && isStrictlyWithin(previewRoot, previewBase)
               && isStrictlyWithin(configuredWorkingDirectory, previewRoot)
               && configuredWorkingDirectory.equals(workingDirectory)
               && isStrictlyWithin(applicationSource, previewRoot)
               && !pathsOverlap(previewRoot, userHome)
               && !pathsOverlap(previewRoot, production)
               && !pathsOverlap(previewRoot, fence)
               && !pathsOverlap(workingDirectory, production)
               && !pathsOverlap(applicationSource, production)
               && !pathsOverlap(workingDirectory, fence)
               && !pathsOverlap(applicationSource, fence)) {
               for (Path boundary : additionalProtectedBoundaries) {
                  Path canonicalBoundary = canonicalProtectedBoundaryOrNull(boundary, pathRealizer);
                  if (canonicalBoundary == null || pathsOverlap(previewRoot, canonicalBoundary)) {
                     return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.INVALID_OR_AMBIGUOUS;
                  }
               }

               return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.VALID_ISOLATED_PREVIEW;
            } else {
               return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.INVALID_OR_AMBIGUOUS;
            }
         } else {
            return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.PRODUCTION_BOUND;
         }
      } else {
         return Round51MaintenanceEnvironmentPostProcessor.RuntimeMode.INVALID_OR_AMBIGUOUS;
      }
   }

   private static String explicitPreviewProperty(ConfigurableEnvironment environment, String propertyName) {
      for (PropertySource<?> propertySource : environment.getPropertySources()) {
         if (!"defaultProperties".equals(propertySource.getName())) {
            Object value = propertySource.getProperty(propertyName);
            if (value != null) {
               return value.toString();
            }
         }
      }

      return null;
   }

   private List<Path> protectedPreviewBoundaries() {
      List<Path> boundaries = new ArrayList<>();
      addProtectedProjectBoundaries(boundaries, SERVER_PROJECT_ROOT);
      addProtectedProjectBoundaries(boundaries, this.serverProjectRoot);
      addProtectedBoundary(boundaries, SERVER_MARKER);
      addProtectedBoundary(boundaries, SERVER_MARKER.getParent());
      addProtectedBoundary(boundaries, SERVER_STARTUP_GATE);
      addProtectedBoundary(boundaries, SERVER_STARTUP_GATE.getParent());
      addProtectedBoundary(boundaries, this.marker);
      addProtectedBoundary(boundaries, this.marker.getParent());
      addProtectedBoundary(boundaries, this.startupGate);
      addProtectedBoundary(boundaries, this.startupGate.getParent());
      return boundaries;
   }

   private static void addProtectedProjectBoundaries(List<Path> boundaries, Path projectRoot) {
      addProtectedBoundary(boundaries, projectRoot);
      if (projectRoot != null) {
         addProtectedBoundary(boundaries, projectRoot.resolve("backend"));
      }
   }

   private static void addProtectedBoundary(List<Path> boundaries, Path boundary) {
      if (boundary != null) {
         boundaries.add(boundary);
      }
   }

   private static Path pathFromExplicitProperty(ConfigurableEnvironment environment, String propertyName) {
      String value = explicitPreviewProperty(environment, propertyName);
      if (value != null && !value.isBlank()) {
         try {
            return Path.of(value);
         } catch (RuntimeException exception) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Path canonicalExistingDirectoryOrNull(Path supplied, Round51MaintenanceEnvironmentPostProcessor.PreviewPathRealizer pathRealizer) {
      if (supplied == null) {
         return null;
      }

      try {
         Path absolute = supplied.toAbsolutePath();
         if (supplied.isAbsolute()
            && absolute.equals(absolute.normalize())
            && !Files.isSymbolicLink(absolute)
            && Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            Path canonical = pathRealizer.toRealPath(absolute);
            return absolute.equals(canonical) && !Files.isSymbolicLink(canonical) && Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS) ? canonical : null;
         } else {
            return null;
         }
      } catch (IOException | RuntimeException exception) {
         return null;
      }
   }

   private static Path canonicalApplicationSourceOrNull(Path supplied, Round51MaintenanceEnvironmentPostProcessor.PreviewPathRealizer pathRealizer) {
      if (supplied == null) {
         return null;
      }

      try {
         Path absolute = supplied.toAbsolutePath();
         if (supplied.isAbsolute()
            && absolute.equals(absolute.normalize())
            && !Files.isSymbolicLink(absolute)
            && Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            Path canonical = pathRealizer.toRealPath(absolute);
            if (!absolute.equals(canonical) || Files.isSymbolicLink(canonical)) {
               return null;
            } else if (Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS)) {
               return canonical;
            } else {
               return Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)
                     && Files.isRegularFile(canonical.resolve("com/auralink/Application.class"), LinkOption.NOFOLLOW_LINKS)
                  ? canonical
                  : null;
            }
         } else {
            return null;
         }
      } catch (IOException | RuntimeException exception) {
         return null;
      }
   }

   private static Path canonicalProtectedBoundaryOrNull(Path boundary, Round51MaintenanceEnvironmentPostProcessor.PreviewPathRealizer pathRealizer) {
      if (boundary == null) {
         return null;
      }

      try {
         Path absolute = boundary.toAbsolutePath().normalize();
         if (Files.notExists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            return absolute;
         }

         if (Files.isSymbolicLink(absolute)) {
            return null;
         }

         Path canonical = pathRealizer.toRealPath(absolute);
         return absolute.equals(canonical) ? canonical : null;
      } catch (IOException | RuntimeException exception) {
         return null;
      }
   }

   private static boolean isFilesystemRoot(Path path) {
      return path.getParent() == null;
   }

   private static boolean isWithinOrEqual(Path candidate, Path root) {
      return candidate.equals(root) || candidate.startsWith(root);
   }

   private static boolean isStrictlyWithin(Path candidate, Path root) {
      return !candidate.equals(root) && candidate.startsWith(root);
   }

   private static boolean pathsOverlap(Path first, Path second) {
      return isWithinOrEqual(first, second) || isWithinOrEqual(second, first);
   }

   private static Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchors defaultRuntimeAnchors() {
      return new Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchors(defaultWorkingDirectory(), defaultApplicationSource(), defaultUserHomeDirectory());
   }

   private static Path defaultWorkingDirectory() {
      try {
         return Path.of("").toAbsolutePath();
      } catch (RuntimeException exception) {
         return null;
      }
   }

   private static Path defaultApplicationSource() {
      try {
         File source = new ApplicationHome(Application.class).getSource();
         if (source != null) {
            return source.toPath();
         }
      } catch (RuntimeException var4) {
      }

      try {
         ProtectionDomain protectionDomain = Application.class.getProtectionDomain();
         CodeSource codeSource = protectionDomain == null ? null : protectionDomain.getCodeSource();
         URL location = codeSource == null ? null : codeSource.getLocation();
         return location != null && "file".equalsIgnoreCase(location.getProtocol()) ? Path.of(location.toURI()) : null;
      } catch (URISyntaxException | RuntimeException exception) {
         return null;
      }
   }

   private static Path defaultUserHomeDirectory() {
      try {
         String value = System.getProperty("user.home", "");
         return value.isBlank() ? null : Path.of(value);
      } catch (RuntimeException exception) {
         return null;
      }
   }

   private void provisionPrivateDirectory(Path directory) {
      try {
         Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      } catch (FileAlreadyExistsException var6) {
      } catch (IOException | UnsupportedOperationException exception) {
         refuse();
      }

      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory) || !this.hasPrivatePermissions(directory)) {
         refuse();
      }

      try (FileChannel parentDirectory = FileChannel.open(directory.getParent(), StandardOpenOption.READ)) {
         parentDirectory.force(true);
      } catch (IOException exception) {
         refuse();
      }
   }

   private void provisionPrivateGate(Path gate) {
      if (Files.notExists(gate, LinkOption.NOFOLLOW_LINKS)) {
         try {
            Files.createFile(gate, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));

            try (FileChannel created = FileChannel.open(gate, StandardOpenOption.WRITE)) {
               created.force(true);
            }

            try (FileChannel directory = FileChannel.open(gate.getParent(), StandardOpenOption.READ)) {
               directory.force(true);
            }
         } catch (FileAlreadyExistsException var9) {
         } catch (IOException | UnsupportedOperationException exception) {
            refuse();
         }
      }

      if (!Files.isRegularFile(gate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(gate) || !this.hasPrivatePermissions(gate)) {
         refuse();
      }
   }

   private boolean hasPrivatePermissions(Path path) {
      try {
         Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
         return permissions.stream().noneMatch(permission -> {
            return switch (permission) {
               case GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE -> true;
               default -> false;
            };
         });
      } catch (IOException | UnsupportedOperationException exception) {
         return false;
      }
   }

   private boolean markerExists() {
      if (Files.notExists(this.marker, LinkOption.NOFOLLOW_LINKS)) {
         return false;
      }

      if (!Files.exists(this.marker, LinkOption.NOFOLLOW_LINKS)
         || Files.isSymbolicLink(this.marker)
         || !Files.isRegularFile(this.marker, LinkOption.NOFOLLOW_LINKS)) {
         refuse();
      }

      return true;
   }

   private void requireOwnerToken(ConfigurableEnvironment environment) {
      String expected = this.readMarkerWithoutFollowingLinks();
      String supplied = environment.getProperty("AURALINK_ROUND51_MAINTENANCE_TOKEN", "");
      if (!TOKEN_PATTERN.matcher(expected).matches()
         || !TOKEN_PATTERN.matcher(supplied).matches()
         || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
         refuse();
      }
   }

   private Round51MaintenanceEnvironmentPostProcessor.GateLease acquireSharedStartupGate() {
      Set<OpenOption> options = Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
      FileChannel channel = null;

      try {
         channel = FileChannel.open(this.startupGate, options);
         FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, true);
         if (lock == null || !lock.isShared()) {
            if (lock != null) {
               lock.release();
            }

            channel.close();
            refuse();
         }

         return new Round51MaintenanceEnvironmentPostProcessor.GateLease(channel, lock);
      } catch (IOException | OverlappingFileLockException exception) {
         if (channel != null) {
            try {
               channel.close();
            } catch (IOException var5) {
            }
         }

         refuse();
         throw new IllegalStateException("unreachable");
      }
   }

   private boolean hasDurableOrphanFence(Path parent) {
      try (DirectoryStream<Path> candidates = Files.newDirectoryStream(parent, ".round51-*-startup-gate-orphan-fence-*")) {
         for (Path candidate : candidates) {
            if (!Files.isSymbolicLink(candidate) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
               return true;
            }

            refuse();
         }

         return false;
      } catch (IOException exception) {
         refuse();
         return true;
      }
   }

   private String readMarkerWithoutFollowingLinks() {
      Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
      ByteBuffer buffer = ByteBuffer.allocate(129);

      try (SeekableByteChannel channel = Files.newByteChannel(this.marker, options)) {
         while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
         }

         if (channel.read(ByteBuffer.allocate(1)) >= 0) {
            refuse();
         }
      } catch (IOException exception) {
         refuse();
      }

      buffer.flip();
      return StandardCharsets.US_ASCII.decode(buffer).toString().trim();
   }

   private static void refuse() {
      throw new IllegalStateException("AURALINK_ROUND51_MAINTENANCE_ACTIVE");
   }

   private record GateLease(FileChannel channel, FileLock lock) {
      private void close() {
         try {
            this.lock.release();
         } catch (IOException var3) {
         }

         try {
            this.channel.close();
         } catch (IOException var2) {
         }
      }
   }

   private record MaintenancePaths(Path marker, Path startupGate, Path serverProjectRoot, boolean automatic) {
   }

   @FunctionalInterface
   interface PreviewPathRealizer {
      Path toRealPath(Path path) throws IOException;
   }

   record RuntimeAnchors(Path workingDirectory, Path applicationSource, Path userHomeDirectory) {
   }

   @FunctionalInterface
   private interface RuntimeAnchorsSupplier {
      Round51MaintenanceEnvironmentPostProcessor.RuntimeAnchors resolve();
   }

   enum RuntimeMode {
      PRODUCTION_BOUND,
      VALID_ISOLATED_PREVIEW,
      OFF_SERVER,
      INVALID_OR_AMBIGUOUS;
   }

   static final class TestPathScope implements AutoCloseable {
      private final Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths previous;
      private final Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths installed;

      private TestPathScope(
         Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths previous, Round51MaintenanceEnvironmentPostProcessor.MaintenancePaths installed
      ) {
         this.previous = previous;
         this.installed = installed;
      }

      @Override
      public void close() {
         if (Round51MaintenanceEnvironmentPostProcessor.TEST_PATHS.get() != this.installed) {
            throw new IllegalStateException("Round 5.1 test path scope closed out of order");
         }

         if (this.previous == null) {
            Round51MaintenanceEnvironmentPostProcessor.TEST_PATHS.remove();
         } else {
            Round51MaintenanceEnvironmentPostProcessor.TEST_PATHS.set(this.previous);
         }
      }
   }
}

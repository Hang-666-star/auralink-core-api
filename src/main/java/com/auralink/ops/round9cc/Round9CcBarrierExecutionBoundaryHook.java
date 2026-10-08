package com.auralink.ops.round9cc;

import com.auralink.creation.CreationExecutionBoundary;
import com.auralink.creation.CreationExecutionBoundaryHook;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

public final class Round9CcBarrierExecutionBoundaryHook implements CreationExecutionBoundaryHook {
   private static final long PARK_NANOS = Duration.ofMillis(25L).toNanos();
   private static final String ARTIFACT_STEP = "RESULT_ARTIFACT";
   private final Path control;
   private final Set<CreationExecutionBoundary> selected;
   private final long timeoutNanos;
   private final Round9CcMockJournal journal;

   public Round9CcBarrierExecutionBoundaryHook(
      Round9CcFixture fixture, String instance, Set<CreationExecutionBoundary> selected, Duration timeout, Round9CcMockJournal journal
   ) {
      this.control = fixture.controlDirectory(instance);
      this.selected = selected != null && !selected.isEmpty() ? Set.copyOf(EnumSet.copyOf(selected)) : Set.of();
      if (timeout != null && !timeout.isNegative() && !timeout.isZero() && timeout.compareTo(Duration.ofMinutes(5L)) <= 0) {
         this.timeoutNanos = timeout.toNanos();
         this.journal = Objects.requireNonNull(journal, "journal");
      } else {
         throw new IllegalArgumentException("ROUND 9C-C failpoint timeout is invalid");
      }
   }

   @Override
   public void reached(CreationExecutionBoundary boundary) {
      if (boundary != null && this.selected.contains(boundary)) {
         Path reached = this.marker(boundary, "reached");
         Path release = this.marker(boundary, "release");
         this.writeReached(reached, boundary);
         long deadline = System.nanoTime() + this.timeoutNanos;

         while (System.nanoTime() < deadline) {
            if (this.released(release)) {
               return;
            }

            LockSupport.parkNanos(PARK_NANOS);
            if (Thread.currentThread().isInterrupted()) {
               Thread.currentThread().interrupt();
               throw new Round9CcFailpointTimeoutException();
            }
         }

         throw new Round9CcFailpointTimeoutException();
      }
   }

   @Override
   public void artifactCloseAttempted() {
      this.journal.closed("RESULT_ARTIFACT");
   }

   private Path marker(CreationExecutionBoundary boundary, String suffix) {
      Path marker = this.control.resolve(boundary.name() + "." + suffix).normalize();
      if (!marker.getParent().equals(this.control)) {
         throw new IllegalStateException("ROUND 9C-C failpoint control is invalid");
      } else {
         return marker;
      }
   }

   private void writeReached(Path reached, CreationExecutionBoundary boundary) {
      try {
         Files.writeString(reached, boundary.name() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
         Round9CcFixture.setPrivateFile(reached);
      } catch (IOException exception) {
         throw new IllegalStateException("ROUND 9C-C failpoint marker could not be recorded");
      }
   }

   private boolean released(Path release) {
      try {
         if (!Files.exists(release, LinkOption.NOFOLLOW_LINKS)) {
            return false;
         } else if (!Files.isSymbolicLink(release) && Files.isRegularFile(release, LinkOption.NOFOLLOW_LINKS)) {
            return true;
         } else {
            throw new IllegalStateException("ROUND 9C-C failpoint release is invalid");
         }
      } catch (SecurityException exception) {
         throw new IllegalStateException("ROUND 9C-C failpoint release is invalid");
      }
   }
}

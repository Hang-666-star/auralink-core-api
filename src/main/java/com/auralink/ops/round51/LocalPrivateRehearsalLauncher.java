package com.auralink.ops.round51;

import com.auralink.Application;
import java.nio.file.Path;

public final class LocalPrivateRehearsalLauncher {
   private LocalPrivateRehearsalLauncher() {
   }

   public static void main(String[] args) {
      String configured = System.getProperty("auralink.local-private-rehearsal.state-dir");
      if (configured != null && !configured.isBlank()) {
         Path workingDirectory = Path.of("").toAbsolutePath().normalize();
         Path stateDirectory = Path.of(configured).toAbsolutePath().normalize();
         if (!stateDirectory.startsWith(workingDirectory)) {
            throw new IllegalArgumentException("private rehearsal state must stay below the candidate backend workspace");
         }

         Round51MaintenanceEnvironmentPostProcessor.installAutomaticPathsForCurrentThread(
            stateDirectory.resolve(".round51-maintenance"), stateDirectory.resolve(".round51-startup-gate"), workingDirectory
         );
         Application.main(args);
      } else {
         throw new IllegalArgumentException("auralink.local-private-rehearsal.state-dir is required");
      }
   }
}

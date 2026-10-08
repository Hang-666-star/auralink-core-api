package com.auralink.ops.round9cc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class Round9CcFixtureManifest {
   private Round9CcFixtureManifest() {
   }

   public static void write(Round9CcFixture fixture, Round9CcScenario scenario) {
      if (fixture != null && scenario != null) {
         Path file = fixture.manifestFile();
         if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) {
            Properties values = new Properties();
            values.putAll(scenario.manifestValues());

            try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
               values.store(output, "ROUND 9C-C disposable scenario");
               Round9CcFixture.setPrivateFile(file);
            } catch (IOException exception) {
               throw new IllegalStateException("ROUND 9C-C scenario manifest could not be recorded");
            }
         } else {
            throw new IllegalArgumentException("ROUND 9C-C scenario manifest is invalid");
         }
      } else {
         throw new IllegalArgumentException("ROUND 9C-C scenario manifest is invalid");
      }
   }
}

package com.auralink.ops.round9cc;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Round9CcFixtureTool {
   private Round9CcFixtureTool() {
   }

   public static void main(String[] arguments) {
      Map<String, String> values = parse(arguments);
      Round9CcFixture fixture = Round9CcFixture.validate(Path.of(require(values, "fixture-root")));
      Round9CcScenario scenario = Round9CcScenario.require(require(values, "scenario"));
      Round9CcFixtureManifest.write(fixture, scenario);
      System.out.println("ROUND9CC_FIXTURE_MANIFEST_OK");
   }

   private static Map<String, String> parse(String[] arguments) {
      Map<String, String> values = new LinkedHashMap<>();
      if (arguments == null) {
         throw new IllegalArgumentException("ROUND 9C-C fixture arguments are invalid");
      }

      String[] var2 = arguments;
      int var3 = var2.length;
      int var4 = 0;

      while (var4 < var3) {
         String argument = var2[var4];
         if (argument != null && argument.startsWith("--") && argument.indexOf(61) >= 3) {
            int separator = argument.indexOf(61);
            String key = argument.substring(2, separator);
            String value = argument.substring(separator + 1);
            if (key.matches("[a-z-]{1,40}") && !value.isBlank() && values.putIfAbsent(key, value) == null) {
               var4++;
               continue;
            }

            throw new IllegalArgumentException("ROUND 9C-C fixture arguments are invalid");
         }

         throw new IllegalArgumentException("ROUND 9C-C fixture arguments are invalid");
      }

      return Map.copyOf(values);
   }

   private static String require(Map<String, String> values, String key) {
      String value = values.get(key);
      if (value != null && !value.isBlank()) {
         return value;
      } else {
         throw new IllegalArgumentException("ROUND 9C-C fixture arguments are invalid");
      }
   }
}

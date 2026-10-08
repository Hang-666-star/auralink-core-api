package com.auralink.ops.round81;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;

final class Round81MockSupport {
   static final String ENABLE_TOKEN = "LOCAL_LOOPBACK_ONLY";
   private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "::1", "localhost");
   private final Environment environment;

   Round81MockSupport(Environment environment) {
      this.environment = environment;
   }

   boolean enabled() {
      return "LOCAL_LOOPBACK_ONLY".equals(this.environment.getProperty("auralink.round81.mock-mode", ""));
   }

   URI endpoint(String fixedPath) {
      URI base = this.requireBase();
      if (fixedPath != null && fixedPath.startsWith("/") && !fixedPath.contains("..")) {
         return URI.create(base.toString() + fixedPath);
      } else {
         throw new Round81ValidationException("MOCK_ENDPOINT_INVALID", "Mock endpoint path is invalid");
      }
   }

   URI generatedImage() {
      return this.endpoint("/mock/generated.png");
   }

   void requireEnabled() {
      if (!this.enabled()) {
         throw new Round81ValidationException("MOCK_MODE_REFUSED", "Packaged Mock mode is not enabled");
      }
   }

   private URI requireBase() {
      this.requireEnabled();
      String raw = this.environment.getProperty("auralink.round81.mock-base-url", "");

      URI uri;
      try {
         uri = new URI(raw.trim());
      } catch (URISyntaxException | NullPointerException exception) {
         throw new Round81ValidationException("MOCK_ENDPOINT_INVALID", "Mock endpoint configuration is invalid", exception);
      }

      String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
      String path = uri.getPath();
      if ("http".equalsIgnoreCase(uri.getScheme())
         && host != null
         && LOOPBACK_HOSTS.contains(host)
         && uri.getPort() >= 1024
         && uri.getPort() <= 65535
         && uri.getUserInfo() == null
         && uri.getQuery() == null
         && uri.getFragment() == null
         && (path == null || path.isEmpty() || "/".equals(path))) {
         return URI.create("http://" + this.hostForUri(host) + ":" + uri.getPort());
      } else {
         throw new Round81ValidationException("MOCK_ENDPOINT_INVALID", "Mock endpoint must be an explicit loopback HTTP origin");
      }
   }

   private String hostForUri(String host) {
      return host.contains(":") ? "[" + host + "]" : host;
   }
}

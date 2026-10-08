package com.auralink.service;

import com.auralink.config.properties.RemoteFetchProperties;
import com.auralink.exception.RemoteResourceRejectedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SafeRemoteResourceFetcher {
   private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
   private static final int BUFFER_SIZE = 8192;
   private static final ScheduledExecutorService DEADLINE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "auralink-remote-fetch-deadline");
      thread.setDaemon(true);
      return thread;
   });
   private final RemoteFetchProperties properties;
   private final SafeRemoteResourceFetcher.HostAddressResolver addressResolver;

   @Autowired
   public SafeRemoteResourceFetcher(RemoteFetchProperties properties) {
      this(properties, InetAddress::getAllByName);
   }

   SafeRemoteResourceFetcher(RemoteFetchProperties properties, SafeRemoteResourceFetcher.HostAddressResolver addressResolver) {
      this.properties = properties;
      this.addressResolver = addressResolver;
   }

   public void fetchTo(String rawUri, Path target) throws IOException {
      URI uri = this.parseAndValidate(rawUri);
      Path normalizedTarget = target.toAbsolutePath().normalize();
      Path parent = normalizedTarget.getParent();
      if (parent == null) {
         throw new IOException("无效的远程资源存储目标");
      }

      Files.createDirectories(parent);
      Path temporary = Files.createTempFile(parent, ".remote-resource-", ".part");

      try {
         try (OutputStream output = Files.newOutputStream(temporary)) {
            this.download(uri, output);
         }

         try {
            Files.move(temporary, normalizedTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temporary);
      }
   }

   URI parseAndValidate(String rawUri) throws IOException {
      if (rawUri != null && !rawUri.isBlank()) {
         URI uri;
         try {
            uri = new URI(rawUri.trim()).normalize();
         } catch (URISyntaxException e) {
            throw new RemoteResourceRejectedException("远程 URL 格式无效");
         }

         this.validateDestination(uri);
         return uri;
      } else {
         throw new RemoteResourceRejectedException("远程 URL 不能为空");
      }
   }

   URI resolveAndValidateRedirect(URI current, String location) throws IOException {
      if (location != null && !location.isBlank()) {
         URI redirect;
         try {
            redirect = current.resolve(new URI(location.trim())).normalize();
         } catch (IllegalArgumentException | URISyntaxException e) {
            throw new RemoteResourceRejectedException("远程服务返回了无效重定向");
         }

         this.validateDestination(redirect);
         return redirect;
      } else {
         throw new RemoteResourceRejectedException("远程服务返回了无效重定向");
      }
   }

   void validateDestination(URI uri) throws IOException {
      this.resolveValidatedDestination(uri);
   }

   private SafeRemoteResourceFetcher.ResolvedDestination resolveValidatedDestination(URI uri) throws IOException {
      String scheme = uri.getScheme();
      if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
         if (uri.getUserInfo() != null) {
            throw new RemoteResourceRejectedException("远程 URL 不得包含用户凭据");
         }

         if (uri.getFragment() != null) {
            throw new RemoteResourceRejectedException("远程 URL 不得包含片段标识");
         }

         int port = uri.getPort();
         if (port >= -1 && port != 0 && port <= 65535) {
            String host = normalizeHost(uri.getHost());
            if (host != null && !isLocalHostName(host)) {
               InetAddress[] addresses;
               try {
                  addresses = this.addressResolver.resolve(host);
               } catch (UnknownHostException e) {
                  throw new RemoteResourceRejectedException("远程 URL 主机无法解析", e);
               }

               if (addresses == null || addresses.length == 0) {
                  throw new RemoteResourceRejectedException("远程 URL 主机无法解析");
               } else if (Arrays.stream(addresses).anyMatch(SafeRemoteResourceFetcher::isDisallowedAddress)) {
                  throw new RemoteResourceRejectedException("远程 URL 指向不允许访问的网络地址");
               } else {
                  return new SafeRemoteResourceFetcher.ResolvedDestination(host, (InetAddress[])addresses.clone());
               }
            } else {
               throw new RemoteResourceRejectedException("远程 URL 主机不允许访问");
            }
         } else {
            throw new RemoteResourceRejectedException("远程 URL 端口无效");
         }
      } else {
         throw new RemoteResourceRejectedException("仅允许 HTTP 或 HTTPS 远程资源");
      }
   }

   private void download(URI initialUri, OutputStream output) throws IOException {
      URI current = initialUri;
      int redirectCount = 0;
      long deadlineNanos = deadlineFromNow(this.properties.getTotalTimeout(), System::nanoTime);

      while (true) {
         ensureBeforeDeadline(deadlineNanos, System::nanoTime);
         SafeRemoteResourceFetcher.ResolvedDestination destination = this.resolveValidatedDestination(current);
         CloseableHttpClient client = this.createPinnedClient(destination);

         label224: {
            try {
               HttpGet request = new HttpGet(current);
               request.setHeader("Accept", "*/*");
               ScheduledFuture<?> cancellation = this.scheduleCancellation(request, deadlineNanos);

               try {
                  label230: {
                     CloseableHttpResponse response = client.execute(request);

                     label231: {
                        try {
                           ensureBeforeDeadline(deadlineNanos, System::nanoTime);
                           int status = response.getCode();
                           if (!REDIRECT_STATUSES.contains(status)) {
                              if (status >= 200 && status < 300) {
                                 long maxBytes = this.properties.getMaxBytes();
                                 if (maxBytes <= 0L) {
                                    throw new IOException("远程资源大小限制配置无效");
                                 }

                                 HttpEntity entity = response.getEntity();
                                 long contentLength = entity == null ? 0L : entity.getContentLength();
                                 if (contentLength > maxBytes) {
                                    throw new RemoteResourceRejectedException("远程资源超过允许的最大大小");
                                 }

                                 if (entity != null) {
                                    try (InputStream input = entity.getContent()) {
                                       copyWithLimit(input, output, maxBytes, deadlineNanos, System::nanoTime);
                                    }
                                 }
                                 break label231;
                              }

                              throw new IOException("远程资源服务返回非成功状态: " + status);
                           }

                           if (redirectCount >= Math.max(0, this.properties.getMaxRedirects())) {
                              throw new RemoteResourceRejectedException("远程资源重定向次数超过限制");
                           }

                           Header location = response.getFirstHeader("Location");
                           current = this.resolveAndValidateRedirect(current, location == null ? null : location.getValue());
                           redirectCount++;
                        } catch (Throwable var34) {
                           if (response != null) {
                              try {
                                 response.close();
                              } catch (Throwable var31) {
                                 var34.addSuppressed(var31);
                              }
                           }

                           throw var34;
                        }

                        if (response != null) {
                           response.close();
                        }
                        break label230;
                     }

                     if (response != null) {
                        response.close();
                     }
                     break label224;
                  }
               } catch (IOException exception) {
                  if (!request.isCancelled() && !isDeadlineExceeded(deadlineNanos, System::nanoTime)) {
                     throw exception;
                  }

                  throw new RemoteResourceRejectedException("远程资源下载超过总时限");
               } finally {
                  cancellation.cancel(false);
               }
            } catch (Throwable var37) {
               if (client != null) {
                  try {
                     client.close();
                  } catch (Throwable var30) {
                     var37.addSuppressed(var30);
                  }
               }

               throw var37;
            }

            if (client != null) {
               client.close();
            }
            continue;
         }

         if (client != null) {
            client.close();
         }

         return;
      }
   }

   private CloseableHttpClient createPinnedClient(SafeRemoteResourceFetcher.ResolvedDestination destination) throws IOException {
      Duration connectTimeout = requirePositiveTimeout(this.properties.getConnectTimeout(), "远程资源连接超时配置无效");
      Duration readTimeout = requirePositiveTimeout(this.properties.getReadTimeout(), "远程资源读取超时配置无效");
      SafeRemoteResourceFetcher.PinnedDnsResolver pinnedDnsResolver = new SafeRemoteResourceFetcher.PinnedDnsResolver(
         destination.host(), destination.addresses()
      );
      ConnectionConfig connectionConfig = ConnectionConfig.custom()
         .setConnectTimeout(Timeout.of(connectTimeout))
         .setSocketTimeout(Timeout.of(readTimeout))
         .build();
      PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
         .setDnsResolver(pinnedDnsResolver)
         .setDefaultConnectionConfig(connectionConfig)
         .setMaxConnTotal(1)
         .setMaxConnPerRoute(1)
         .build();
      RequestConfig requestConfig = RequestConfig.custom()
         .setRedirectsEnabled(false)
         .setAuthenticationEnabled(false)
         .setConnectionRequestTimeout(Timeout.of(connectTimeout))
         .setResponseTimeout(Timeout.of(readTimeout))
         .setContentCompressionEnabled(false)
         .setHardCancellationEnabled(true)
         .build();
      return HttpClients.custom()
         .setConnectionManager(connectionManager)
         .setDefaultRequestConfig(requestConfig)
         .disableRedirectHandling()
         .disableAutomaticRetries()
         .disableAuthCaching()
         .disableCookieManagement()
         .disableContentCompression()
         .build();
   }

   static long copyWithLimit(InputStream input, OutputStream output, long maxBytes) throws IOException {
      return copyWithLimit(input, output, maxBytes, Long.MAX_VALUE, () -> 0L);
   }

   static long copyWithLimit(InputStream input, OutputStream output, long maxBytes, long deadlineNanos, LongSupplier nanoTime) throws IOException {
      if (maxBytes <= 0L) {
         throw new IllegalArgumentException("maxBytes must be positive");
      }

      byte[] buffer = new byte[8192];
      long total = 0L;

      while (true) {
         ensureBeforeDeadline(deadlineNanos, nanoTime);
         int read = input.read(buffer);
         ensureBeforeDeadline(deadlineNanos, nanoTime);
         if (read == -1) {
            return total;
         }

         if (read > maxBytes - total) {
            throw new RemoteResourceRejectedException("远程资源超过允许的最大大小");
         }

         output.write(buffer, 0, read);
         total += read;
      }
   }

   private ScheduledFuture<?> scheduleCancellation(HttpGet request, long deadlineNanos) throws IOException {
      long remainingNanos = remainingNanos(deadlineNanos, System::nanoTime);
      if (remainingNanos <= 0L) {
         throw new RemoteResourceRejectedException("远程资源下载超过总时限");
      } else {
         return DEADLINE_SCHEDULER.schedule(request::cancel, remainingNanos, TimeUnit.NANOSECONDS);
      }
   }

   private static long deadlineFromNow(Duration timeout, LongSupplier nanoTime) throws IOException {
      Duration positiveTimeout = requirePositiveTimeout(timeout, "远程资源总时限配置无效");

      long timeoutNanos;
      try {
         timeoutNanos = positiveTimeout.toNanos();
      } catch (ArithmeticException exception) {
         throw new IOException("远程资源总时限配置无效");
      }

      return nanoTime.getAsLong() + timeoutNanos;
   }

   private static Duration requirePositiveTimeout(Duration timeout, String message) throws IOException {
      if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
         return timeout;
      } else {
         throw new IOException(message);
      }
   }

   private static void ensureBeforeDeadline(long deadlineNanos, LongSupplier nanoTime) throws IOException {
      if (isDeadlineExceeded(deadlineNanos, nanoTime)) {
         throw new RemoteResourceRejectedException("远程资源下载超过总时限");
      }
   }

   private static boolean isDeadlineExceeded(long deadlineNanos, LongSupplier nanoTime) {
      return remainingNanos(deadlineNanos, nanoTime) <= 0L;
   }

   private static long remainingNanos(long deadlineNanos, LongSupplier nanoTime) {
      return deadlineNanos - nanoTime.getAsLong();
   }

   static boolean isDisallowedAddress(InetAddress address) {
      if (address != null
         && !address.isAnyLocalAddress()
         && !address.isLoopbackAddress()
         && !address.isLinkLocalAddress()
         && !address.isSiteLocalAddress()
         && !address.isMulticastAddress()) {
         byte[] bytes = address.getAddress();
         if (bytes.length == 4) {
            return isDisallowedIpv4(bytes);
         } else if (bytes.length != 16) {
            return true;
         } else {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            if ((first & 254) != 252
               && (first != 254 || (second & 192) != 128)
               && first != 255
               && !isIpv6DocumentationRange(bytes)
               && !isIpv4MappedAddress(bytes)) {
               return false;
            } else {
               return isIpv4MappedAddress(bytes) ? isDisallowedIpv4(Arrays.copyOfRange(bytes, 12, 16)) : true;
            }
         }
      } else {
         return true;
      }
   }

   private static boolean isDisallowedIpv4(byte[] bytes) {
      int a = Byte.toUnsignedInt(bytes[0]);
      int b = Byte.toUnsignedInt(bytes[1]);
      int c = Byte.toUnsignedInt(bytes[2]);
      return a == 0
         || a == 10
         || a == 127
         || a == 100 && b >= 64 && b <= 127
         || a == 169 && b == 254
         || a == 172 && b >= 16 && b <= 31
         || a == 192 && b == 0
         || a == 192 && b == 168
         || a == 198 && (b == 18 || b == 19)
         || a == 198 && b == 51 && c == 100
         || a == 203 && b == 0 && c == 113
         || a >= 224;
   }

   private static boolean isIpv4MappedAddress(byte[] bytes) {
      for (int i = 0; i < 10; i++) {
         if (bytes[i] != 0) {
            return false;
         }
      }

      return bytes[10] == -1 && bytes[11] == -1;
   }

   private static boolean isIpv6DocumentationRange(byte[] bytes) {
      return Byte.toUnsignedInt(bytes[0]) == 32
         && Byte.toUnsignedInt(bytes[1]) == 1
         && Byte.toUnsignedInt(bytes[2]) == 13
         && Byte.toUnsignedInt(bytes[3]) == 184;
   }

   private static String normalizeHost(String host) {
      if (host == null) {
         return null;
      }

      String normalized = host.toLowerCase(Locale.ROOT);

      while (normalized.endsWith(".")) {
         normalized = normalized.substring(0, normalized.length() - 1);
      }

      return normalized;
   }

   private static boolean isLocalHostName(String host) {
      return !host.contains(".") && !host.contains(":")
         || host.equals("localhost")
         || host.endsWith(".localhost")
         || host.equals("localhost.localdomain")
         || host.endsWith(".localdomain")
         || host.endsWith(".local")
         || host.endsWith(".internal")
         || host.endsWith(".lan")
         || host.endsWith(".home");
   }

   @FunctionalInterface
   interface HostAddressResolver {
      InetAddress[] resolve(String host) throws UnknownHostException;
   }

   static final class PinnedDnsResolver implements DnsResolver {
      private final String expectedHost;
      private final InetAddress[] addresses;

      PinnedDnsResolver(String expectedHost, InetAddress[] addresses) {
         this.expectedHost = SafeRemoteResourceFetcher.normalizeHost(expectedHost);
         this.addresses = (InetAddress[])addresses.clone();
      }

      public InetAddress[] resolve(String host) throws UnknownHostException {
         this.requireExpectedHost(host);
         return (InetAddress[])this.addresses.clone();
      }

      public String resolveCanonicalHostname(String host) throws UnknownHostException {
         this.requireExpectedHost(host);
         return this.expectedHost;
      }

      private void requireExpectedHost(String host) throws UnknownHostException {
         if (!this.expectedHost.equals(SafeRemoteResourceFetcher.normalizeHost(host))) {
            throw new UnknownHostException("Unpinned host rejected");
         }
      }
   }

   private record ResolvedDestination(String host, InetAddress[] addresses) {
      private ResolvedDestination {
         addresses = (InetAddress[])addresses.clone();
      }

      public InetAddress[] addresses() {
         return (InetAddress[])this.addresses.clone();
      }
   }
}

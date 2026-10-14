package com.auralink.assistant;

import com.auralink.config.properties.MediaAssetProperties;
import com.auralink.exception.StorageException;
import com.auralink.service.media.ImageContentValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Private, flat, Assistant-only namespace. This class never uses catalog/media storage roots. */
public final class AssistantAttachmentStorage {
   // Matches V014's persisted byte_size constraint. Upload quotas may be lower without hiding old images.
   private static final long MAX_PERSISTED_BYTES = 10 * 1024 * 1024;
   private static final String MARKER_NAME = ".assistant-attachments-v1";
   private static final byte[] MARKER = "ArtLIVE Assistant private attachments v1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
   private final Path root;
   private final Object rootFileKey;
   private final long maxBytes;
   private final ImageContentValidator validator;

   public AssistantAttachmentStorage(Path root, long maxBytes, long maxImagePixels) {
      if (root == null || !root.isAbsolute() || root.normalize().getParent() == null
         || maxBytes < 1 || maxBytes > MAX_PERSISTED_BYTES || maxImagePixels < 1) {
         throw new IllegalArgumentException("A dedicated absolute Assistant attachment directory and positive limits are required");
      }
      this.root = root.normalize();
      this.maxBytes = maxBytes;
      MediaAssetProperties imageProperties = new MediaAssetProperties();
      imageProperties.setMaxImagePixels(maxImagePixels);
      this.validator = new ImageContentValidator(imageProperties);
      try {
         requireSafeAncestors(this.root);
         Files.createDirectories(this.root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
         requireSafeAncestors(this.root);
         var permissions = Files.getPosixFilePermissions(this.root, LinkOption.NOFOLLOW_LINKS);
         if (permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) {
            throw new IllegalArgumentException("Assistant attachment directory must be private (0700)");
         }
         this.rootFileKey = Files.readAttributes(this.root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
         if (rootFileKey == null) throw new IllegalArgumentException("Assistant storage requires stable filesystem file identities");
         initializeNamespace();
      } catch (IOException exception) {
         throw new StorageException("Assistant attachment directory is unavailable", exception);
      }
   }

   private void initializeNamespace() throws IOException {
      Path marker = root.resolve(MARKER_NAME);
      if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
         try (var entries = Files.list(root)) {
            if (entries.findAny().isPresent()) throw new StorageException("Assistant attachment directory is not an empty or recognized namespace");
         }
         try (FileChannel channel = FileChannel.open(marker,
            java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer bytes = ByteBuffer.wrap(MARKER);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
         }
         try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
      }
      var attributes = requireFile(marker);
      if (attributes.size() != MARKER.length) throw new StorageException("Assistant attachment namespace marker is invalid");
      try (InputStream input = Files.newInputStream(marker, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
         if (!java.util.Arrays.equals(MARKER, input.readNBytes(MARKER.length + 1))) {
            throw new StorageException("Assistant attachment namespace marker is invalid");
         }
      }
   }

   /** Bound input before reserving its durable record; no filesystem write occurs here. */
   public byte[] readUpload(InputStream input) {
      try (input) {
         byte[] bytes = input.readNBytes((int) maxBytes + 1);
         if (bytes.length == 0 || bytes.length > maxBytes) {
            throw new IllegalArgumentException("Assistant image is empty or exceeds the configured byte limit");
         }
         return bytes;
      } catch (IOException exception) {
         throw new StorageException("Assistant upload could not be read", exception);
      }
   }

   /** Capacity is a preflight hint; actual write/fsync errors still refuse READY. No global disk guarantee. */
   public void requireCapacity(long bytes,long minimumFree) {
      requireRoot();
      try { if(bytes<0||minimumFree<0||Files.getFileStore(root).getUsableSpace()-minimumFree<bytes)throw new AssistantFailure(507,"ASSISTANT_DISK_FULL","附件磁盘空间不足，本次上传未完成；已保存内容仍可读取"); }
      catch(IOException failure){throw new StorageException("Assistant disk capacity could not be checked",failure);}
   }
   /** Observed bytes, including both known .part and .blob if an interrupted operation left either. */
   public long retainedBytes(UUID id) {
      long bytes=0;
      try { for(String suffix:new String[]{".part",".blob"}){Path path=path(id,suffix);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))bytes=Math.addExact(bytes,requireFile(path).size());}return bytes; }
      catch(IOException failure){throw new StorageException("Assistant retained bytes could not be checked",failure);}
   }

   /** Caller must first reserve UPLOADING in PostgreSQL. Both intermediate paths are derived from that ID. */
   public ImageContentValidator.ValidatedImage write(UUID id, byte[] bytes, String expectedHash, String mimeType, String filename) {
      if (bytes == null || bytes.length == 0 || bytes.length > maxBytes || !sha256(bytes).equals(expectedHash)) {
         throw new IllegalArgumentException("Assistant attachment bytes do not match their reservation");
      }
      Path part = path(id, ".part");
      Path target = path(id, ".blob");
      try {
         if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new StorageException("Assistant attachment destination already exists");
         try (FileChannel channel = FileChannel.open(part,
            java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
         }
         var image = validator.validateUpload(part, mimeType, filename);
         if (!sha256(Files.readAllBytes(part)).equals(expectedHash)) throw new StorageException("Assistant staged attachment changed");
         requireRoot();
         Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
         // Directory fsync makes the rename durable before the database advertises READY.
         try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
         return image;
      } catch (IOException exception) {
         // Leave tracked UPLOADING/FAILED bytes for explicit, bounded cleanup. Never delete other paths here.
         if(exception instanceof java.nio.file.FileSystemException fs && fs.getReason()!=null && fs.getReason().toLowerCase(java.util.Locale.ROOT).matches(".*(no space left|disk quota exceeded).*") )
            throw new AssistantFailure(507,"ASSISTANT_DISK_FULL","附件磁盘空间不足，本次上传未完成；已保存内容仍可读取");
         throw new StorageException("Assistant attachment could not be stored", exception);
      }
   }

   public byte[] read(UUID id, long expectedSize, String expectedHash) {
      Path file = path(id, ".blob");
      try {
         var before = requireFile(file);
         if (expectedSize < 1 || expectedSize > MAX_PERSISTED_BYTES || before.size() != expectedSize) {
            throw new StorageException("Assistant attachment size does not match its record");
         }
         byte[] bytes;
         try (InputStream input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes((int) expectedSize + 1);
         }
         var after = requireFile(file);
         if (!before.fileKey().equals(after.fileKey()) || bytes.length != expectedSize || !sha256(bytes).equals(expectedHash)) {
            throw new StorageException("Assistant attachment integrity check failed");
         }
         return bytes;
      } catch (IOException exception) {
         throw new StorageException("Assistant attachment is unavailable", exception);
      }
   }

   public void inspectCleanup(UUID id,long expectedSize,String expectedHash) {
      Path blob=path(id,".blob"),part=path(id,".part");
      if(Files.exists(blob,LinkOption.NOFOLLOW_LINKS))read(id,expectedSize,expectedHash);
      try {if(Files.exists(part,LinkOption.NOFOLLOW_LINKS)&&requireFile(part).size()>expectedSize)throw new StorageException("Assistant partial attachment exceeded its namespace limit");}
      catch(IOException failure){throw new StorageException("Assistant cleanup inspection failed",failure);}
   }

   /** Only tracked IDs are eligible. A final blob must match its recorded preimage before deletion. */
   public void remove(UUID id, long expectedSize, String expectedHash) {
      Path blob = path(id, ".blob");
      Path part = path(id, ".part");
      try {
         if (Files.exists(blob, LinkOption.NOFOLLOW_LINKS)) {
            var before = requireFile(blob);
            read(id, expectedSize, expectedHash);
            deleteUnchanged(blob, before);
         }
         if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)) {
            // Partial files intentionally may not match the final hash after an interrupted write.
            var before = requireFile(part);
            if (before.size() > expectedSize) throw new StorageException("Assistant partial attachment exceeded its namespace limit");
            deleteUnchanged(part, before);
         }
         try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
      } catch (IOException exception) {
         throw new StorageException("Assistant attachment cleanup failed", exception);
      }
   }

   private void deleteUnchanged(Path path, BasicFileAttributes before) throws IOException {
      var after = requireFile(path);
      if (!before.fileKey().equals(after.fileKey()) || before.size() != after.size()
         || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
         throw new StorageException("Assistant attachment changed during cleanup");
      }
      requireRoot();
      Files.delete(path);
   }

   private Path path(UUID id, String suffix) {
      if (id == null) throw new IllegalArgumentException("Attachment ID is required");
      requireRoot();
      return root.resolve(id + suffix);
   }

   private BasicFileAttributes requireFile(Path file) throws IOException {
      requireRoot();
      var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.fileKey() == null) {
         throw new StorageException("Assistant attachment must be a regular private file");
      }
      if (((Number) Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1) {
         throw new StorageException("Assistant attachments must not be hard linked");
      }
      if (Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS).stream()
         .anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_") || p.name().endsWith("EXECUTE"))) {
         throw new StorageException("Assistant attachments must remain private and non-executable");
      }
      return attributes;
   }

   private void requireRoot() {
      try {
         requireSafeAncestors(root);
         if (!rootFileKey.equals(Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey())) {
            throw new StorageException("Assistant attachment root identity changed");
         }
      } catch (IOException exception) {
         throw new StorageException("Assistant attachment root is unavailable", exception);
      }
   }

   private static void requireSafeAncestors(Path path) throws IOException {
      Path current = path.getRoot();
      for (Path part : path) {
         current = current.resolve(part);
         if (Files.isSymbolicLink(current)) throw new StorageException("Assistant storage may not traverse symbolic links");
         if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new StorageException("Assistant storage ancestor is not a directory");
         }
      }
   }

   public static String sha256(byte[] bytes) {
      try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
      catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
   }
}

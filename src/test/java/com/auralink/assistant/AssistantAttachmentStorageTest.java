package com.auralink.assistant;

import com.auralink.exception.StorageException;
import com.auralink.service.media.InvalidImageContentException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssistantAttachmentStorageTest {
   @TempDir Path directory;

   @Test void bytesRemainPrivateAndSurviveStorageRestart() throws Exception {
      Path root = directory.resolve("assistant");
      byte[] bytes = png(4, 3);
      String digest = AssistantAttachmentStorage.sha256(bytes);
      UUID id = UUID.randomUUID();
      var first = new AssistantAttachmentStorage(root, 1024, 32_000_000);
      var image = first.write(id, first.readUpload(new ByteArrayInputStream(bytes)), digest, "image/png", "画.png");
      assertEquals(4, image.width());
      assertEquals(3, image.height());
      assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(root)));
      assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve(id + ".blob"))));
      var restarted = new AssistantAttachmentStorage(root, 1024, 32_000_000);
      assertArrayEquals(bytes, restarted.read(id, bytes.length, digest));
      assertThrows(StorageException.class, () -> restarted.read(id, bytes.length, "0".repeat(64)));
      restarted.remove(id, bytes.length, digest);
      assertFalse(Files.exists(root.resolve(id + ".blob")));
   }

   @Test void rejectsOversizeMimeMismatchAndTooManyPixels() throws Exception {
      var storage = new AssistantAttachmentStorage(directory.resolve("assistant"), 1024, 3);
      assertThrows(IllegalArgumentException.class, () -> storage.readUpload(new ByteArrayInputStream(new byte[1025])));
      assertThrows(IllegalArgumentException.class, () -> storage.readUpload(new ByteArrayInputStream(new byte[0])));
      byte[] bytes = png(2, 2);
      UUID pixelId = UUID.randomUUID();
      assertThrows(InvalidImageContentException.class, () -> storage.write(pixelId, bytes,
         AssistantAttachmentStorage.sha256(bytes), "image/png", "test.png"));
      storage.remove(pixelId, bytes.length, AssistantAttachmentStorage.sha256(bytes));
      var larger = new AssistantAttachmentStorage(directory.resolve("assistant"), 1024, 100);
      UUID mimeId = UUID.randomUUID();
      assertThrows(InvalidImageContentException.class, () -> larger.write(mimeId, bytes,
         AssistantAttachmentStorage.sha256(bytes), "image/jpeg", "test.jpg"));
      larger.remove(mimeId, bytes.length, AssistantAttachmentStorage.sha256(bytes));
   }

   @Test void cleanupOfTrackedPartialDoesNotTouchOutsideOrUntrackedFiles() throws Exception {
      Path root = directory.resolve("assistant");
      var storage = new AssistantAttachmentStorage(root, 1024, 100);
      UUID id = UUID.randomUUID();
      Path part = root.resolve(id + ".part");
      Files.write(part, new byte[]{1, 2, 3});
      Files.setPosixFilePermissions(part, PosixFilePermissions.fromString("rw-------"));
      Path unrelated = Files.write(root.resolve("unrelated.txt"), new byte[]{7});
      Path outside = Files.write(directory.resolve("outside"), new byte[]{9});
      storage.remove(id, 100, "0".repeat(64));
      assertFalse(Files.exists(part));
      assertArrayEquals(new byte[]{7}, Files.readAllBytes(unrelated));
      assertArrayEquals(new byte[]{9}, Files.readAllBytes(outside));
   }

   @Test void rejectsSymlinkHardlinkAndTamperedBlobWithoutDeletingTargets() throws Exception {
      Path root = directory.resolve("assistant");
      var storage = new AssistantAttachmentStorage(root, 1024, 100);
      byte[] bytes = png(1, 1);
      String digest = AssistantAttachmentStorage.sha256(bytes);
      Path outside = Files.write(directory.resolve("outside.png"), bytes);
      Files.setPosixFilePermissions(outside, PosixFilePermissions.fromString("rw-------"));
      UUID symlinkId = UUID.randomUUID();
      Files.createSymbolicLink(root.resolve(symlinkId + ".part"), outside);
      assertThrows(StorageException.class, () -> storage.remove(symlinkId, bytes.length, digest));
      assertArrayEquals(bytes, Files.readAllBytes(outside));
      UUID hardlinkId = UUID.randomUUID();
      Files.createLink(root.resolve(hardlinkId + ".blob"), outside);
      assertThrows(StorageException.class, () -> storage.remove(hardlinkId, bytes.length, digest));
      assertArrayEquals(bytes, Files.readAllBytes(outside));
      UUID tampered = UUID.randomUUID();
      storage.write(tampered, bytes, digest, "image/png", "test.png");
      Files.write(root.resolve(tampered + ".blob"), new byte[]{5, 6});
      assertThrows(StorageException.class, () -> storage.remove(tampered, bytes.length, digest));
      assertTrue(Files.exists(root.resolve(tampered + ".blob")));
   }

   @Test void refusesRootSymlinkAndRuntimeRootReplacement() throws Exception {
      Path target = Files.createDirectory(directory.resolve("target"));
      Path link = directory.resolve("link");
      Files.createSymbolicLink(link, target);
      assertThrows(StorageException.class, () -> new AssistantAttachmentStorage(link, 1024, 100));
      Path root = directory.resolve("assistant");
      var storage = new AssistantAttachmentStorage(root, 1024, 100);
      Files.move(root, directory.resolve("old-assistant"));
      Files.createDirectory(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      assertThrows(StorageException.class, () -> storage.read(UUID.randomUUID(), 1, "0".repeat(64)));
   }

   @Test void rejectsUnmarkedExistingContentAndCorruptNamespaceMarker() throws Exception {
      Path root = Files.createDirectory(directory.resolve("private-unrelated"),
         PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      Path unrelated = Files.write(root.resolve("private-document"), new byte[]{1, 2});
      assertThrows(StorageException.class, () -> new AssistantAttachmentStorage(root, 1024, 100));
      assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(unrelated));
      Path assistant = directory.resolve("assistant");
      new AssistantAttachmentStorage(assistant, 1024, 100);
      Files.writeString(assistant.resolve(".assistant-attachments-v1"), "wrong namespace");
      assertThrows(StorageException.class, () -> new AssistantAttachmentStorage(assistant, 1024, 100));
   }

   @Test void diskPathConflictPreservesReservationPreimage() throws Exception {
      Path root = directory.resolve("assistant");
      var storage = new AssistantAttachmentStorage(root, 1024, 100);
      UUID id = UUID.randomUUID();
      Path part = Files.write(root.resolve(id + ".part"), new byte[]{4, 5, 6});
      Files.setPosixFilePermissions(part, PosixFilePermissions.fromString("rw-------"));
      byte[] png = png(1, 1);
      assertThrows(StorageException.class, () -> storage.write(id, png, AssistantAttachmentStorage.sha256(png), "image/png", "test.png"));
      assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(part));
      assertFalse(Files.exists(root.resolve(id + ".blob")));
   }

   @Test void lowerNewUploadQuotaDoesNotHideOrBlockDeletionOfHistoricalImage() throws Exception {
      Path root = directory.resolve("assistant");
      var original = new AssistantAttachmentStorage(root, 1024, 100);
      byte[] bytes = png(2, 2);
      String hash = AssistantAttachmentStorage.sha256(bytes);
      UUID id = UUID.randomUUID();
      original.write(id, bytes, hash, "image/png", "test.png");
      var lowerQuota = new AssistantAttachmentStorage(root, 16, 100);
      assertThrows(IllegalArgumentException.class, () -> lowerQuota.readUpload(new ByteArrayInputStream(bytes)));
      assertArrayEquals(bytes, lowerQuota.read(id, bytes.length, hash));
      lowerQuota.remove(id, bytes.length, hash);
      assertFalse(Files.exists(root.resolve(id + ".blob")));
   }

   static byte[] png(int width, int height) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes));
      return bytes.toByteArray();
   }
}

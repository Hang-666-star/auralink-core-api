package com.auralink.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CatalogSourceSnapshotFactory {
   private static final byte[] FINGERPRINT_VERSION = "auralink-official-painting-snapshot-v1\n".getBytes(StandardCharsets.UTF_8);
   private final OfficialPaintingCsvReader csvReader;
   private final PaintingImageMatcher imageMatcher;

   public CatalogSourceSnapshot create(Path csvPath, Path pictureDirectory) {
      Path source = this.requirePath(csvPath, "Official painting CSV path is required");
      String csvShaBeforeRead = this.sha256(source);
      List<OfficialPaintingRecord> records = this.csvReader.read(source);
      String csvShaAfterRead = this.sha256(source);
      if (!csvShaBeforeRead.equals(csvShaAfterRead)) {
         throw new CatalogSourceException("Official painting CSV changed while its snapshot was created");
      }

      CatalogImageManifest manifest = this.imageMatcher.scan(pictureDirectory);
      List<CatalogSourceRow> rows = new ArrayList<>(records.size());
      Set<String> sourceKeys = new HashSet<>();
      Set<String> matchedFileNames = new LinkedHashSet<>();

      for (OfficialPaintingRecord record : records) {
         if (!sourceKeys.add(record.sourceKey())) {
            throw new CatalogSourceException("Official painting CSV contains a duplicate source key");
         }

         String matched = this.imageMatcher.match(record.imageStorageName(), manifest).orElse(null);
         rows.add(new CatalogSourceRow(record, matched));
         if (matched != null) {
            matchedFileNames.add(matched);
         }
      }

      List<String> orphans = manifest.entries().stream().map(ImageManifestEntry::fileName).filter(fileName -> !matchedFileNames.contains(fileName)).toList();
      String fingerprint = this.combinedFingerprint(csvShaAfterRead, manifest);
      String sourceName = source.getFileName() == null ? "paintings.csv" : source.getFileName().toString();
      return new CatalogSourceSnapshot(sourceName, csvShaAfterRead, fingerprint, rows, orphans);
   }

   private String combinedFingerprint(String csvSha256, CatalogImageManifest manifest) {
      MessageDigest digest = this.newDigest();
      digest.update(FINGERPRINT_VERSION);
      this.updateDelimited(digest, csvSha256);

      for (ImageManifestEntry entry : manifest.entries()) {
         this.updateDelimited(digest, entry.fileName());
         this.updateDelimited(digest, Long.toString(entry.size()));
         this.updateDelimited(digest, Long.toString(entry.lastModifiedMillis()));
      }

      return HexFormat.of().formatHex(digest.digest());
   }

   private void updateDelimited(MessageDigest digest, String value) {
      digest.update(value.getBytes(StandardCharsets.UTF_8));
      digest.update((byte)0);
   }

   private String sha256(Path path) {
      MessageDigest digest = this.newDigest();

      try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
         input.transferTo(OutputStream.nullOutputStream());
         return HexFormat.of().formatHex(digest.digest());
      } catch (IOException exception) {
         throw new CatalogSourceException("Official painting CSV fingerprint could not be calculated", exception);
      }
   }

   private MessageDigest newDigest() {
      try {
         return MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException exception) {
         throw new IllegalStateException("SHA-256 is unavailable", exception);
      }
   }

   private Path requirePath(Path path, String message) {
      if (path == null) {
         throw new CatalogSourceException(message);
      } else {
         return path.toAbsolutePath().normalize();
      }
   }

   public CatalogSourceSnapshotFactory(final OfficialPaintingCsvReader csvReader, final PaintingImageMatcher imageMatcher) {
      this.csvReader = csvReader;
      this.imageMatcher = imageMatcher;
   }
}

package com.auralink.service;

import com.auralink.config.properties.PaintingProperties;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaintingCatalogService {
   private static final Logger log = LoggerFactory.getLogger(PaintingCatalogService.class);
   private final PaintingProperties paintingConfig;
   private final AtomicBoolean loaded = new AtomicBoolean(false);
   private volatile List<PaintingCatalogService.PaintingEntry> cachedEntries = List.of();
   private volatile Map<String, String> imageNameMapping = Map.of();

   public PaintingCatalogService.PaintingPage search(String query, String dynasty, Integer limit, Integer offset) {
      this.ensureLoaded();
      int safeLimit = this.normalizeLimit(limit);
      int safeOffset = Math.max(0, offset == null ? 0 : offset);
      String normalizedQuery = this.normalizeLower(query);
      String normalizedDynasty = this.normalizeLower(dynasty);
      List<PaintingCatalogService.PaintingEntry> filtered = this.cachedEntries
         .stream()
         .filter(entry -> this.matchQuery(entry, normalizedQuery))
         .filter(entry -> this.matchDynasty(entry, normalizedDynasty))
         .toList();
      int total = filtered.size();
      int start = Math.min(safeOffset, total);
      int end = Math.min(start + safeLimit, total);
      List<Map<String, Object>> items = filtered.subList(start, end).stream().map(PaintingCatalogService.PaintingEntry::payload).toList();
      return new PaintingCatalogService.PaintingPage(total, safeLimit, safeOffset, items);
   }

   public Optional<Path> resolveImagePath(String fileNameOrStorageName) {
      Path pictureDir = this.getPictureDirPath();
      if (!Files.isDirectory(pictureDir)) {
         return Optional.empty();
      }

      String input = this.sanitizeInput(fileNameOrStorageName);
      if (input.isBlank()) {
         return Optional.empty();
      }

      Map<String, String> mappingSnapshot = this.imageNameMapping;
      Set<String> candidates = new LinkedHashSet<>();
      String mappedByRaw = mappingSnapshot.get(input);
      if (StringUtils.hasText(mappedByRaw)) {
         candidates.add(mappedByRaw);
      }

      String mappedByNormalized = mappingSnapshot.get(this.normalizeStorageName(input));
      if (StringUtils.hasText(mappedByNormalized)) {
         candidates.add(mappedByNormalized);
      }

      candidates.addAll(this.buildFileNameCandidates(input));

      for (String candidate : candidates) {
         Path resolved = pictureDir.resolve(candidate).normalize();
         if (resolved.startsWith(pictureDir) && Files.isRegularFile(resolved)) {
            return Optional.of(resolved);
         }
      }

      return Optional.empty();
   }

   private void ensureLoaded() {
      if (!this.loaded.get()) {
         synchronized (this.loaded) {
            if (!this.loaded.get()) {
               this.reload();
               this.loaded.set(true);
            }
         }
      }
   }

   private void reload() {
      Path csvPath = this.getMetadataCsvPath();
      Path pictureDir = this.getPictureDirPath();
      if (!Files.isRegularFile(csvPath)) {
         throw new IllegalStateException("Painting catalog metadata is unavailable");
      }

      List<PaintingCatalogService.PaintingEntry> entries = new ArrayList<>();
      Map<String, String> mapping = new LinkedHashMap<>();
      CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(new String[0]).setSkipHeaderRecord(true).setQuote('"').build();

      try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
         CSVParser parser = format.parse(reader);

         try {
            List<String> headers = parser.getHeaderNames();

            for (CSVRecord record : parser) {
               LinkedHashMap<String, Object> payload = new LinkedHashMap<>();

               for (String header : headers) {
                  payload.put(header, this.normalizeCell(record.get(header)));
               }

               String id = this.getCell(record, 0);
               String imageStorageName = this.getCell(record, 1);
               String title = this.getCell(record, 2);
               String author = this.getCell(record, 3);
               String dynasty = this.getCell(record, 8);
               String category = this.getCell(record, 11);
               String resolvedImageFileName = this.resolveExistingImageFileName(imageStorageName, pictureDir).orElse("");
               payload.put("id", id);
               payload.put("imageStorageName", imageStorageName);
               payload.put("imageFileName", resolvedImageFileName);
               payload.put("imageAvailable", !resolvedImageFileName.isBlank());
               payload.put("title", title);
               payload.put("author", author);
               payload.put("dynasty", dynasty);
               payload.put("category", category);
               if (!imageStorageName.isBlank() && !resolvedImageFileName.isBlank()) {
                  mapping.put(imageStorageName, resolvedImageFileName);
                  mapping.put(this.normalizeStorageName(imageStorageName), resolvedImageFileName);
               }

               if (!resolvedImageFileName.isBlank()) {
                  mapping.put(resolvedImageFileName, resolvedImageFileName);
               }

               String searchableText = String.join(
                  " ", title, author, dynasty, category, this.getCell(record, 12), this.getCell(record, 13), this.getCell(record, 14)
               );
               entries.add(new PaintingCatalogService.PaintingEntry(payload, this.normalizeLower(searchableText), this.normalizeLower(dynasty)));
            }
         } catch (Throwable var22) {
            if (parser != null) {
               try {
                  parser.close();
               } catch (Throwable var21) {
                  var22.addSuppressed(var21);
               }
            }

            throw var22;
         }

         if (parser != null) {
            parser.close();
         }
      } catch (IOException e) {
         throw new IllegalStateException("Failed to load paintings metadata CSV: " + e.getMessage(), e);
      }

      this.cachedEntries = List.copyOf(entries);
      this.imageNameMapping = Map.copyOf(mapping);
      log.info("Loaded paintings metadata: {} records, {} image mappings", entries.size(), mapping.size());
   }

   private Optional<String> resolveExistingImageFileName(String imageStorageName, Path pictureDir) {
      if (!StringUtils.hasText(imageStorageName)) {
         return Optional.empty();
      }

      for (String candidate : this.buildFileNameCandidates(imageStorageName)) {
         Path resolved = pictureDir.resolve(candidate).normalize();
         if (resolved.startsWith(pictureDir) && Files.isRegularFile(resolved)) {
            return Optional.of(resolved.getFileName().toString());
         }
      }

      return Optional.empty();
   }

   private List<String> buildFileNameCandidates(String rawName) {
      String sanitized = this.sanitizeInput(rawName);
      if (sanitized.isBlank()) {
         return List.of();
      }

      String withExt = this.ensureJpgExtension(sanitized);
      String normalized = this.normalizeStorageName(withExt);
      String withSpaceBeforeParen = normalized.replaceAll("(?<=\\d)\\(", " (");
      String noSpaceBeforeParen = normalized.replaceAll("\\s+\\(", "(");
      LinkedHashSet<String> candidates = new LinkedHashSet<>();
      candidates.add(withExt);
      candidates.add(normalized);
      candidates.add(withSpaceBeforeParen);
      candidates.add(noSpaceBeforeParen);
      return List.copyOf(candidates);
   }

   private String ensureJpgExtension(String value) {
      String trimmed = value.trim();
      String lower = trimmed.toLowerCase(Locale.ROOT);
      return !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") ? trimmed + ".jpg" : trimmed;
   }

   private String sanitizeInput(String value) {
      if (value == null) {
         return "";
      }

      String normalized = value.trim().replace('\\', '/');
      int slash = normalized.lastIndexOf(47);
      if (slash >= 0) {
         normalized = normalized.substring(slash + 1);
      }

      return normalized.trim();
   }

   private String normalizeStorageName(String value) {
      return value.replace('（', '(').replace('）', ')').replace('（', '(').replace('）', ')').replaceAll("\\s+", " ").trim();
   }

   private int normalizeLimit(Integer limit) {
      int defaultLimit = this.paintingConfig.getDefaultLimit() == null ? 500 : this.paintingConfig.getDefaultLimit();
      int maxLimit = this.paintingConfig.getMaxLimit() == null ? 2000 : this.paintingConfig.getMaxLimit();
      int chosen = limit == null ? defaultLimit : limit;
      return chosen <= 0 ? defaultLimit : Math.min(chosen, maxLimit);
   }

   private boolean matchQuery(PaintingCatalogService.PaintingEntry entry, String normalizedQuery) {
      return !StringUtils.hasText(normalizedQuery) ? true : entry.searchable().contains(normalizedQuery);
   }

   private boolean matchDynasty(PaintingCatalogService.PaintingEntry entry, String normalizedDynasty) {
      return !StringUtils.hasText(normalizedDynasty) ? true : entry.dynasty().contains(normalizedDynasty);
   }

   private String normalizeLower(String value) {
      return !StringUtils.hasText(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
   }

   private String normalizeCell(String value) {
      return value == null ? "" : value.trim();
   }

   private String getCell(CSVRecord record, int index) {
      return index >= 0 && index < record.size() ? this.normalizeCell(record.get(index)) : "";
   }

   private Path getMetadataCsvPath() {
      String configured = this.paintingConfig.getMetadataCsvPath();
      List<String> candidates = new ArrayList<>();
      if (StringUtils.hasText(configured)) {
         candidates.add(configured.trim());
      }

      candidates.add("../app/assets/paintings.csv");
      candidates.add("app/assets/paintings.csv");
      candidates.add("./app/assets/paintings.csv");

      for (String candidate : candidates) {
         Path path = Paths.get(candidate).toAbsolutePath().normalize();
         if (Files.isRegularFile(path)) {
            return path;
         }
      }

      return Paths.get(candidates.get(0)).toAbsolutePath().normalize();
   }

   private Path getPictureDirPath() {
      return Paths.get(this.paintingConfig.getPictureDir()).toAbsolutePath().normalize();
   }

   public PaintingCatalogService(final PaintingProperties paintingConfig) {
      this.paintingConfig = paintingConfig;
   }

   private record PaintingEntry(Map<String, Object> payload, String searchable, String dynasty) {
   }

   public record PaintingPage(int total, int limit, int offset, List<Map<String, Object>> items) {
   }
}

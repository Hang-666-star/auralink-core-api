package com.auralink.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class PaintingImageMatcher {
   public CatalogImageManifest scan(Path pictureDirectory) {
      Path root = this.requirePictureDirectory(pictureDirectory);

      try (Stream<Path> files = Files.list(root)) {
         List<ImageManifestEntry> entries = files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            .filter(path -> this.isSupportedImageFileName(path.getFileName().toString()))
            .map(this::manifestEntry)
            .toList();
         return new CatalogImageManifest(entries);
      } catch (CatalogSourceException exception) {
         throw exception;
      } catch (IOException | RuntimeException exception) {
         throw new CatalogSourceException("Catalog image manifest could not be created", exception);
      }
   }

   public Optional<String> match(String imageStorageName, CatalogImageManifest manifest) {
      if (manifest == null) {
         throw new CatalogSourceException("Catalog image manifest is required");
      }

      LinkedHashSet<String> matches = new LinkedHashSet<>();

      for (String candidate : this.candidatesFor(imageStorageName)) {
         manifest.findIgnoringCase(candidate).map(ImageManifestEntry::fileName).ifPresent(matches::add);
      }

      if (matches.size() > 1) {
         throw new CatalogSourceException("Official painting image storage name resolves ambiguously");
      } else {
         return matches.stream().findFirst();
      }
   }

   public List<String> candidatesFor(String rawName) {
      String sanitized = this.sanitizeInput(rawName);
      if (sanitized.isBlank()) {
         return List.of();
      }

      LinkedHashSet<String> candidates = new LinkedHashSet<>();

      for (String withExtension : this.withSupportedExtensions(sanitized)) {
         String normalized = this.normalizeStorageName(withExtension);
         String withSpaceBeforeParenthesis = normalized.replaceAll("(?<=\\d)\\(", " (");
         String withoutSpaceBeforeParenthesis = normalized.replaceAll("\\s+\\(", "(");
         candidates.add(withExtension);
         candidates.add(normalized);
         candidates.add(withSpaceBeforeParenthesis);
         candidates.add(withoutSpaceBeforeParenthesis);
      }

      return List.copyOf(candidates);
   }

   private ImageManifestEntry manifestEntry(Path file) {
      try {
         String fileName = file.getFileName().toString();
         return new ImageManifestEntry(fileName, Files.size(file), Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis());
      } catch (IOException exception) {
         throw new CatalogSourceException("Catalog image metadata could not be read", exception);
      }
   }

   private Path requirePictureDirectory(Path configured) {
      if (configured == null) {
         throw new CatalogSourceException("Catalog picture directory is required");
      }

      try {
         Path normalized = configured.toAbsolutePath().normalize();
         if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(normalized)) {
            return normalized.toRealPath();
         } else {
            throw new CatalogSourceException("Catalog picture directory is unavailable");
         }
      } catch (CatalogSourceException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new CatalogSourceException("Catalog picture directory could not be resolved", exception);
      }
   }

   private List<String> withSupportedExtensions(String value) {
      String trimmed = value.trim();
      String lower = trimmed.toLowerCase(Locale.ROOT);
      return !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") ? List.of(trimmed + ".jpg", trimmed + ".jpeg") : List.of(trimmed);
   }

   private String sanitizeInput(String value) {
      if (value == null) {
         return "";
      } else {
         String normalized = value.trim();
         if (!this.containsControl(normalized)
            && normalized.indexOf(47) < 0
            && normalized.indexOf(92) < 0
            && !normalized.matches("^[A-Za-z]:.*")
            && !normalized.equals(".")
            && !normalized.equals("..")) {
            return normalized;
         } else {
            throw new CatalogSourceException("Official painting image storage name must be a safe filename");
         }
      }
   }

   private String normalizeStorageName(String value) {
      return value.replace('（', '(').replace('）', ')').replaceAll("\\s+", " ").trim();
   }

   private boolean containsControl(String value) {
      return value.codePoints().anyMatch(Character::isISOControl);
   }

   private boolean isSupportedImageFileName(String fileName) {
      String lower = fileName.toLowerCase(Locale.ROOT);
      return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
   }
}

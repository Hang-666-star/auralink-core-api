package com.auralink.service.media;

import com.auralink.config.properties.MediaAssetProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageContentValidator {
   private static final byte[] PNG_SIGNATURE = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
   private static final byte[] PNG_IEND = new byte[]{0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126};
   private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
   private static final Set<String> PNG_EXTENSIONS = Set.of("png");
   private final MediaAssetProperties properties;

   public ImageContentValidator.ValidatedImage validateUpload(Path path, String declaredMimeType, String originalFilename) {
      ImageContentValidator.ValidatedImage image = this.validateTrustedImage(path);
      this.validateDeclaredMime(declaredMimeType, image.mimeType());
      this.validateFilenameExtension(originalFilename, image.formatName());
      return image;
   }

   public ImageContentValidator.ValidatedImage validateTrustedImage(Path path) {
      this.requireRegularFile(path);
      ImageContentValidator.DetectedFormat signatureFormat = this.detectSignatureAndTerminalMarker(path);

      try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
         if (input == null) {
            throw new InvalidImageContentException("Image bytes cannot be decoded");
         }

         Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
         if (!readers.hasNext()) {
            throw new InvalidImageContentException("Unsupported or invalid image bytes");
         }

         ImageReader reader = readers.next();

         try {
            reader.setInput(input, true, true);
            ImageContentValidator.DetectedFormat readerFormat = ImageContentValidator.DetectedFormat.fromImageIoName(reader.getFormatName());
            if (readerFormat != signatureFormat) {
               throw new InvalidImageContentException("Image signature and decoder format disagree");
            } else {
               int width = reader.getWidth(0);
               int height = reader.getHeight(0);
               this.validateDimensions(width, height);
               BufferedImage decoded = reader.read(0);
               if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                  throw new InvalidImageContentException("Image could not be fully decoded");
               } else {
                  return new ImageContentValidator.ValidatedImage(
                     signatureFormat.formatName, signatureFormat.mimeType, signatureFormat.extension, width, height
                  );
               }
            }
         } finally {
            reader.dispose();
         }
      } catch (InvalidImageContentException exception) {
         throw exception;
      } catch (IOException | RuntimeException exception) {
         throw new InvalidImageContentException("Image could not be safely decoded", exception);
      }
   }

   private void requireRegularFile(Path path) {
      if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
         throw new InvalidImageContentException("Image resource is not a regular file");
      }
   }

   private ImageContentValidator.DetectedFormat detectSignatureAndTerminalMarker(Path path) {
      try {
         long size = Files.size(path);
         if (size < PNG_IEND.length) {
            throw new InvalidImageContentException("Image data is incomplete");
         }

         byte[] prefix = this.readAt(path, 0L, PNG_SIGNATURE.length);
         if (this.startsWith(prefix, PNG_SIGNATURE)) {
            byte[] suffix = this.readAt(path, size - PNG_IEND.length, PNG_IEND.length);
            if (!this.startsWith(suffix, PNG_IEND)) {
               throw new InvalidImageContentException("PNG terminal marker is missing");
            } else {
               return ImageContentValidator.DetectedFormat.PNG;
            }
         } else if ((prefix[0] & 255) == 255 && (prefix[1] & 255) == 216 && (prefix[2] & 255) == 255) {
            byte[] suffix = this.readAt(path, size - 2L, 2);
            if ((suffix[0] & 255) == 255 && (suffix[1] & 255) == 217) {
               return ImageContentValidator.DetectedFormat.JPEG;
            } else {
               throw new InvalidImageContentException("JPEG terminal marker is missing");
            }
         } else {
            throw new InvalidImageContentException("Only JPEG and PNG image bytes are accepted");
         }
      } catch (InvalidImageContentException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new InvalidImageContentException("Image bytes could not be inspected", exception);
      }
   }

   private byte[] readAt(Path path, long position, int length) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(length);

      try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
         channel.position(position);

         while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
               throw new InvalidImageContentException("Image data is incomplete");
            }
         }
      }

      return buffer.array();
   }

   private boolean startsWith(byte[] bytes, byte[] expected) {
      if (bytes.length < expected.length) {
         return false;
      }

      for (int index = 0; index < expected.length; index++) {
         if (bytes[index] != expected[index]) {
            return false;
         }
      }

      return true;
   }

   private void validateDimensions(int width, int height) {
      if (width > 0 && height > 0) {
         long pixels;
         try {
            pixels = Math.multiplyExact((long)width, (long)height);
         } catch (ArithmeticException exception) {
            throw new InvalidImageContentException("Image dimensions exceed the configured limit", exception);
         }

         if (pixels > this.properties.getMaxImagePixels()) {
            throw new InvalidImageContentException("Image pixel count exceeds the configured limit");
         }
      } else {
         throw new InvalidImageContentException("Image dimensions must be positive");
      }
   }

   private void validateDeclaredMime(String declaredMimeType, String detectedMimeType) {
      if (declaredMimeType != null && !declaredMimeType.isBlank()) {
         String normalized = declaredMimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
         if (!detectedMimeType.equals(normalized)) {
            throw new InvalidImageContentException("Declared MIME type does not match image bytes");
         }
      } else {
         throw new InvalidImageContentException("Image MIME type is required");
      }
   }

   private void validateFilenameExtension(String originalFilename, String detectedFormat) {
      if (originalFilename != null && !originalFilename.isBlank()) {
         String basename = originalFilename.replace('\\', '/');
         basename = basename.substring(basename.lastIndexOf(47) + 1);
         int dot = basename.lastIndexOf(46);
         if (dot >= 0 && dot != basename.length() - 1) {
            String extension = basename.substring(dot + 1).toLowerCase(Locale.ROOT);
            Set<String> expected = "JPEG".equals(detectedFormat) ? JPEG_EXTENSIONS : PNG_EXTENSIONS;
            if (!expected.contains(extension)) {
               throw new InvalidImageContentException("Filename extension does not match image bytes");
            }
         } else {
            throw new InvalidImageContentException("Image filename must include a supported extension");
         }
      } else {
         throw new InvalidImageContentException("Image filename must include a supported extension");
      }
   }

   public ImageContentValidator(final MediaAssetProperties properties) {
      this.properties = properties;
   }

   private enum DetectedFormat {
      JPEG("JPEG", "image/jpeg", "jpg"),
      PNG("PNG", "image/png", "png");

      private final String formatName;
      private final String mimeType;
      private final String extension;

      DetectedFormat(String formatName, String mimeType, String extension) {
         this.formatName = formatName;
         this.mimeType = mimeType;
         this.extension = extension;
      }

      private static ImageContentValidator.DetectedFormat fromImageIoName(String name) {
         if (name == null) {
            throw new InvalidImageContentException("Image decoder did not identify a format");
         }

         return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "JPEG", "JPG" -> JPEG;
            case "PNG" -> PNG;
            default -> throw new InvalidImageContentException("Only JPEG and PNG are accepted");
         };
      }
   }

   public record ValidatedImage(String formatName, String mimeType, String fileExtension, int width, int height) {
   }
}

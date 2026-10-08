package com.auralink.provider.artifact;

import com.auralink.creation.provider.ProviderErrorCategory;
import com.auralink.creation.provider.ProviderExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.stereotype.Component;

@Component
public class AudioOutputValidator {
   public void validateWave(Path path, String declaredMimeType, long maxBytes) {
      if (!"audio/wav".equals(declaredMimeType)) {
         throw this.invalid("Provider audio MIME type is invalid");
      }

      if (path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
         try {
            long size = Files.size(path);
            if (size >= 12L && size <= maxBytes) {
               ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);

               try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
                  while (header.hasRemaining()) {
                     if (channel.read(header) < 0) {
                        throw this.invalid("Provider audio header is incomplete");
                     }
                  }
               }

               byte[] bytes = header.array();
               if (this.matches(bytes, 0, "RIFF") && this.matches(bytes, 8, "WAVE")) {
                  long declaredSize = Integer.toUnsignedLong(header.getInt(4));
                  if (declaredSize != size - 8L) {
                     throw this.invalid("Provider audio RIFF length is inconsistent");
                  }

                  try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
                     this.measureWave(input);
                  }
               } else {
                  throw this.invalid("Provider audio is not RIFF/WAVE");
               }
            } else {
               throw this.invalid("Provider audio size is invalid");
            }
         } catch (ProviderExecutionException exception) {
            throw exception;
         } catch (IOException exception) {
            throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider audio could not be validated", exception);
         }
      } else {
         throw this.invalid("Provider audio is not a regular file");
      }
   }

   public AudioOutputValidator.WaveMetadata measureWave(InputStream input) {
      if (input == null) {
         throw this.invalid("Provider audio stream is required");
      }

      try {
         byte[] header = this.readExactly(input, 12, "Provider audio header is incomplete");
         if (this.matches(header, 0, "RIFF") && this.matches(header, 8, "WAVE")) {
            Integer audioFormat = null;
            Integer channels = null;
            Integer sampleRate = null;
            Integer blockAlign = null;
            Integer bitsPerSample = null;
            Long dataBytes = null;

            while (true) {
               byte[] chunkHeader = this.readChunkHeader(input);
               if (chunkHeader == null) {
                  if (audioFormat != null
                     && channels != null
                     && sampleRate != null
                     && blockAlign != null
                     && bitsPerSample != null
                     && dataBytes != null
                     && channels >= 1
                     && sampleRate >= 1
                     && blockAlign >= 1
                     && bitsPerSample >= 1
                     && dataBytes >= 1L
                     && dataBytes % blockAlign.intValue() == 0L) {
                     long sampleFrames = dataBytes / blockAlign.intValue();
                     double durationSeconds = (double)sampleFrames / sampleRate.intValue();
                     if (Double.isFinite(durationSeconds) && !(durationSeconds <= 0.0)) {
                        return new AudioOutputValidator.WaveMetadata(
                           audioFormat, channels, sampleRate, blockAlign, bitsPerSample, sampleFrames, durationSeconds
                        );
                     }

                     throw this.invalid("Provider audio duration is invalid");
                  }

                  throw this.invalid("Provider audio frame layout is invalid");
               }

               long chunkSize = Integer.toUnsignedLong(ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
               if (this.matches(chunkHeader, 0, "fmt ")) {
                  if (audioFormat != null || chunkSize < 16L) {
                     throw this.invalid("Provider audio format chunk is invalid");
                  }

                  byte[] format = this.readExactly(input, 16, "Provider audio format chunk is incomplete");
                  ByteBuffer values = ByteBuffer.wrap(format).order(ByteOrder.LITTLE_ENDIAN);
                  audioFormat = Short.toUnsignedInt(values.getShort());
                  channels = Short.toUnsignedInt(values.getShort());
                  sampleRate = values.getInt();
                  values.getInt();
                  blockAlign = Short.toUnsignedInt(values.getShort());
                  bitsPerSample = Short.toUnsignedInt(values.getShort());
                  this.skipExactly(input, chunkSize - 16L, "Provider audio format chunk is incomplete");
               } else if (this.matches(chunkHeader, 0, "data")) {
                  if (dataBytes != null) {
                     throw this.invalid("Provider audio has multiple data chunks");
                  }

                  dataBytes = chunkSize;
                  this.skipExactly(input, chunkSize, "Provider audio data chunk is incomplete");
               } else {
                  this.skipExactly(input, chunkSize, "Provider audio chunk is incomplete");
               }

               if ((chunkSize & 1L) != 0L) {
                  this.skipExactly(input, 1L, "Provider audio chunk padding is incomplete");
               }
            }
         } else {
            throw this.invalid("Provider audio is not RIFF/WAVE");
         }
      } catch (ProviderExecutionException exception) {
         throw exception;
      } catch (IOException exception) {
         throw new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, "Provider audio could not be measured", exception);
      }
   }

   private byte[] readChunkHeader(InputStream input) throws IOException {
      int first = input.read();
      if (first < 0) {
         return null;
      }

      byte[] chunkHeader = new byte[8];
      chunkHeader[0] = (byte)first;
      this.readInto(input, chunkHeader, 1, 7, "Provider audio chunk header is incomplete");
      return chunkHeader;
   }

   private byte[] readExactly(InputStream input, int length, String message) throws IOException {
      byte[] bytes = new byte[length];
      this.readInto(input, bytes, 0, length, message);
      return bytes;
   }

   private void readInto(InputStream input, byte[] bytes, int offset, int remaining, String message) throws IOException {
      int position = offset;
      int left = remaining;

      while (left > 0) {
         int read = input.read(bytes, position, left);
         if (read < 0) {
            throw this.invalid(message);
         }

         if (read == 0) {
            int value = input.read();
            if (value < 0) {
               throw this.invalid(message);
            }

            bytes[position++] = (byte)value;
            left--;
         } else {
            position += read;
            left -= read;
         }
      }
   }

   private void skipExactly(InputStream input, long remaining, String message) throws IOException {
      long left = remaining;

      while (left > 0L) {
         long skipped = input.skip(left);
         if (skipped > 0L) {
            left -= skipped;
         } else {
            if (input.read() < 0) {
               throw this.invalid(message);
            }

            left--;
         }
      }
   }

   private boolean matches(byte[] bytes, int offset, String value) {
      for (int index = 0; index < value.length(); index++) {
         if (bytes[offset + index] != (byte)value.charAt(index)) {
            return false;
         }
      }

      return true;
   }

   private ProviderExecutionException invalid(String message) {
      return new ProviderExecutionException(ProviderErrorCategory.PROVIDER_OUTPUT_INVALID, message);
   }

   public record WaveMetadata(int audioFormat, int channels, int sampleRate, int blockAlign, int bitsPerSample, long sampleFrames, double durationSeconds) {
   }
}

package com.auralink.ops.round9cc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class Round9CcMockJournal {
   private final Path file;
   private final String scenario;
   private final String role;
   private final AtomicLong nextSequence;

   public Round9CcMockJournal(Round9CcFixture fixture, String scenario, String role) {
      Round9CcFixture.validateLabel(scenario);
      Round9CcFixture.validateInstance(role);
      this.file = fixture.journalFile(role);
      this.scenario = scenario;
      this.role = role;
      ensurePrivateJournal(this.file);
      this.nextSequence = new AtomicLong(read(this.file).size());
   }

   private static void ensurePrivateJournal(Path file) {
      try {
         if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
               Files.createFile(file);
            } catch (FileAlreadyExistsException var2) {
            }
         }

         if (!Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            Round9CcFixture.setPrivateFile(file);
         } else {
            throw new IOException("journal file invalid");
         }
      } catch (IOException exception) {
         throw new IllegalStateException("ROUND 9C-C Mock journal could not be recorded");
      }
   }

   public void entry(String step) {
      this.append(step, Round9CcMockJournal.Event.ENTRY);
   }

   public void returned(String step) {
      this.append(step, Round9CcMockJournal.Event.RETURN);
   }

   public void closed(String step) {
      this.append(step, Round9CcMockJournal.Event.CLOSE);
   }

   public synchronized void append(String step, Round9CcMockJournal.Event event) {
      Round9CcFixture.validateLabel(step);
      if (event == null) {
         throw new IllegalArgumentException("ROUND 9C-C journal event is invalid");
      }

      String line = this.nextSequence.incrementAndGet() + "|" + this.scenario + "|" + this.role + "|" + step + "|" + event.name() + "\n";

      try (FileChannel channel = FileChannel.open(this.file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
         if (Files.isSymbolicLink(this.file)) {
            throw new IOException("journal symlink");
         }

         Round9CcFixture.setPrivateFile(this.file);

         try (FileLock ignored = channel.tryLock()) {
            if (ignored == null) {
               throw new IOException("journal lock unavailable");
            }

            channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
         }
      } catch (IOException exception) {
         throw new IllegalStateException("ROUND 9C-C Mock journal could not be recorded");
      }
   }

   public static List<Round9CcMockJournal.Record> read(Path file) {
      if (file != null && Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
         try {
            if (!Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
               List<Round9CcMockJournal.Record> records = new ArrayList<>();
               long expected = 1L;

               for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                  String[] values = line.split("\\|", -1);
                  if (values.length != 5) {
                     throw new IllegalArgumentException("ROUND 9C-C Mock journal is invalid");
                  }

                  long sequence = Long.parseLong(values[0]);
                  Round9CcFixture.validateLabel(values[1]);
                  Round9CcFixture.validateInstance(values[2]);
                  Round9CcFixture.validateLabel(values[3]);
                  Round9CcMockJournal.Event event = Round9CcMockJournal.Event.valueOf(values[4]);
                  if (sequence != expected++) {
                     throw new IllegalArgumentException("ROUND 9C-C Mock journal is invalid");
                  }

                  records.add(new Round9CcMockJournal.Record(sequence, values[1], values[2], values[3], event));
               }

               return List.copyOf(records);
            } else {
               throw new IllegalArgumentException("ROUND 9C-C Mock journal is invalid");
            }
         } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("ROUND 9C-C Mock journal is invalid");
         }
      } else {
         return List.of();
      }
   }

   Path file() {
      return this.file;
   }

   public enum Event {
      ENTRY,
      RETURN,
      CLOSE;
   }

   public record Record(long sequence, String scenario, String role, String step, Round9CcMockJournal.Event event) {
   }
}

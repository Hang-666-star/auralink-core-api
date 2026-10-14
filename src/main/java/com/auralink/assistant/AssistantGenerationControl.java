package com.auralink.assistant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Fixed operator-owned stop marker; no billing policy, HTTP mutation or lifecycle owner. */
public final class AssistantGenerationControl {
 public static final String NAME=".generation-suspended-v1";
 public static final String CONTENT="ArtLIVE Assistant generation suspended v1\n";
 private static final Set<PosixFilePermission> DIRECTORY=PosixFilePermissions.fromString("rwx------");
 private static final Set<PosixFilePermission> FILE=PosixFilePermissions.fromString("rw-------");
 private final Path root;
 private final Object rootKey;
 private final UserPrincipal owner;
 public AssistantGenerationControl(Path root){
  this.root=root.toAbsolutePath().normalize();
  try{var attrs=Files.readAttributes(this.root,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);rootKey=attrs.fileKey();owner=attrs.owner();}
  catch(IOException error){throw new IllegalArgumentException("Assistant control root is unavailable");}
 }
 public String state(){
  try{
   for(Path path=root;path!=null;path=path.getParent())if(Files.isSymbolicLink(path))throw new IOException("Unsafe root");
   var directory=Files.readAttributes(root,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
   if(!directory.isDirectory()||rootKey==null||!rootKey.equals(directory.fileKey())||!owner.equals(directory.owner())||!DIRECTORY.equals(directory.permissions()))throw new IOException("Root drift");
   Path marker=root.resolve(NAME);PosixFileAttributes before;
   try{before=Files.readAttributes(marker,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
   catch(NoSuchFileException absent){return "AVAILABLE";}
   byte[] expected=CONTENT.getBytes(StandardCharsets.US_ASCII);
   if(!before.isRegularFile()||before.size()!=expected.length||!owner.equals(before.owner())||!FILE.equals(before.permissions())||!Integer.valueOf(1).equals(Files.getAttribute(marker,"unix:nlink",LinkOption.NOFOLLOW_LINKS)))throw new IOException("Invalid marker");
   ByteBuffer bytes=ByteBuffer.allocate(expected.length+1);
   try(var channel=FileChannel.open(marker,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){while(bytes.hasRemaining()&&channel.read(bytes)!=-1){}}
   var after=Files.readAttributes(marker,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
   if(before.fileKey()==null||!before.fileKey().equals(after.fileKey())||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!before.permissions().equals(after.permissions())||!before.owner().equals(after.owner())||bytes.position()!=expected.length||!Arrays.equals(expected,Arrays.copyOf(bytes.array(),bytes.position())))throw new IOException("Marker drift");
   return "ASSISTANT_GENERATION_SUSPENDED";
  }catch(IOException|RuntimeException refused){return "ASSISTANT_GENERATION_CONTROL_UNAVAILABLE";}
 }
}

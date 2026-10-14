package com.auralink.assistant;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Bounded keyset pages. Cursors contain only public object identities; never authorization. */
public record AssistantPage<T>(List<T> items,String nextCursor,boolean hasMore) {
 public <R> AssistantPage<R> map(Function<T,R> mapper){return new AssistantPage<>(items.stream().map(mapper).toList(),nextCursor,hasMore);}
 static void limit(int limit){if(limit<1||limit>100)throw AssistantFailure.invalid("Page limit must be between 1 and 100");}
 static record Cursor(Instant at,UUID id,UUID head){}
 static Cursor cursor(String encoded,String scope){
  if(encoded==null||encoded.isBlank())return null;
  if(encoded.length()>1024)throw AssistantFailure.invalid("Invalid page cursor");
  try{String[] fields=new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8).split("\\n",-1);
   if(fields.length!=5||!fields[0].equals("1")||!fields[1].equals(scope))throw new IllegalArgumentException();
   return new Cursor(fields[2].isEmpty()?null:Instant.parse(fields[2]),UUID.fromString(fields[3]),fields[4].isEmpty()?null:UUID.fromString(fields[4]));
  }catch(RuntimeException invalid){throw AssistantFailure.invalid("Invalid or mismatched page cursor");}
 }
 static String cursor(String scope,Instant at,UUID id,UUID head){return Base64.getUrlEncoder().withoutPadding().encodeToString(("1\n"+scope+"\n"+(at==null?"":at)+"\n"+id+"\n"+(head==null?"":head)).getBytes(StandardCharsets.UTF_8));}
 static <T>AssistantPage<T> of(List<T> found,int limit,Function<T,String> cursor){
  boolean more=found.size()>limit;var items=List.copyOf(found.subList(0,Math.min(limit,found.size())));
  return new AssistantPage<>(items,more?cursor.apply(items.get(items.size()-1)):null,more);
 }
}

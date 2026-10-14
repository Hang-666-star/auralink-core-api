package com.auralink.assistant;

import java.io.*;
import java.nio.charset.*;
import java.util.function.BiConsumer;

/** Bounded, incremental UTF-8 SSE. Invalid bytes and EOF without an explicit terminal event refuse completion. */
public final class AssistantSseReader {
 private AssistantSseReader(){}
 public static void read(InputStream stream,int maxFrame,int maxTotal,BiConsumer<String,String> consumer) throws IOException {
  Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
  StringBuilder line=new StringBuilder(),data=new StringBuilder(); String event="message"; int total=0;boolean previousCr=false,terminal=false;
  for(int c;(c=reader.read())!=-1;){
   if(++total>maxTotal)throw new IOException("Assistant stream exceeds limit");
   if(c=='\n'&&previousCr){previousCr=false;continue;}
   previousCr=c=='\r';
   if(c=='\r'||c=='\n'){
    String text=line.toString();line.setLength(0);
    if(text.isEmpty()){
     if(data.length()>0){String value=data.substring(0,data.length()-1);consumer.accept(event,value);if(event.equals("done")||event.equals("error"))terminal=true;}
     data.setLength(0);event="message";
     if(terminal)return;
    }else if(!text.startsWith(":")){
     int colon=text.indexOf(':');String key=colon<0?text:text.substring(0,colon);String value=colon<0?"":text.substring(colon+1);if(value.startsWith(" "))value=value.substring(1);
     if(key.equals("event"))event=value;
     if(key.equals("data")){data.append(value).append('\n');if(data.length()>maxFrame)throw new IOException("Assistant event exceeds limit");}
    }
   }else{line.append((char)c);if(line.length()>maxFrame)throw new IOException("Assistant line exceeds limit");}
  }
  throw new EOFException("Assistant stream ended without terminal event");
 }
}

package com.auralink.assistant;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Provider-only derivative strips original metadata and bounds image size; original private bytes stay unchanged. */
final class AssistantImages {
 private AssistantImages(){}
 record Image(Map<String,Object> wire,Map<String,Object> manifest){}
 static Image prepare(byte[] original,String mime,String identity){
  if(original.length>10*1024*1024||!Set.of("image/png","image/jpeg").contains(mime))throw AssistantFailure.invalid("Only bounded JPEG/PNG image inputs are supported");
  try{
   BufferedImage source;
   // Read dimensions before decoding to keep compressed pixel bombs bounded.
   try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(original))){
    if(input==null)throw new IOException();
    Iterator<ImageReader> readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();
    ImageReader reader=readers.next();
    try{reader.setInput(input,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
     if(!(mime.equals("image/png")&&format.equals("png")||mime.equals("image/jpeg")&&(format.equals("jpeg")||format.equals("jpg"))))throw new IOException();
     int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||((long)width*height)>32000000)throw new IOException();
     source=reader.read(0);if(source==null)throw new IOException();
    }finally{reader.dispose();}
   }
   double scale=Math.min(1,2048.0/Math.max(source.getWidth(),source.getHeight()));int w=Math.max(1,(int)(source.getWidth()*scale)),h=Math.max(1,(int)(source.getHeight()*scale));
   BufferedImage image=new BufferedImage(w,h,mime.equals("image/png")?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}
   ByteArrayOutputStream output=new ByteArrayOutputStream();if(!ImageIO.write(image,mime.equals("image/png")?"png":"jpg",output))throw new IOException();byte[] bytes=output.toByteArray();if(bytes.length>8*1024*1024)throw new IOException();
   return new Image(Map.of("mimeType",mime,"dataBase64",Base64.getEncoder().encodeToString(bytes)),Map.of("id",identity,"originalSha256",AssistantAttachmentStorage.sha256(original),"derivativeSha256",AssistantAttachmentStorage.sha256(bytes),"width",w,"height",h,"transform","metadata-stripped-fit-2048-v1"));
  }catch(IOException e){throw AssistantFailure.invalid("Image cannot be prepared for the selected model");}
 }
}

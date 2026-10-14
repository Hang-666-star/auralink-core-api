package com.auralink.catalogread;

import com.auralink.assistant.AssistantFailure;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Assistant-specific public projection; canonical Gallery revision and eligibility are shared verbatim. */
public final class AssistantArtworkRead {
 public static final String PROJECTION="assistant-public-artwork-v1";
 private final NamedParameterJdbcTemplate jdbc;
 private final org.springframework.transaction.support.TransactionTemplate snapshot;
 private static final List<String> FIELDS=List.of("core.title","core.author_name","core.category","legacy.title","legacy.painting_name","legacy.author_name","legacy.category","legacy.creation_dynasty_raw","legacy.subject","legacy.painting_school","legacy.style","legacy.color","legacy.composition","legacy.artistic_conception","legacy.brushwork","legacy.ink_method","legacy.painting_material","legacy.cultural_symbol","annotation.style","annotation.subject_secondary");
 public AssistantArtworkRead(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;this.snapshot=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getJdbcTemplate().getDataSource())));snapshot.setReadOnly(true);snapshot.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setTimeout(5);}
 public record Artwork(UUID artworkId,UUID batchId,int revision,UUID mediaAssetId,@com.fasterxml.jackson.annotation.JsonIgnore String annotationFileId,int recordNumber,String projectionVersion,List<Field> fields){
  @com.fasterxml.jackson.annotation.JsonProperty public String sourceRecordId(){
   String identity=batchId+"\n"+revision+"\n"+annotationFileId.length()+":"+annotationFileId+"\n"+recordNumber;
   try{return "sha256:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
 }
 public record Field(String key,String value,String rawValue,String valueStatus,String assertionStatus){}
 public List<Artwork> search(String query,List<UUID> ids,int limit){return snapshot.execute(status->read(query,ids,limit));}
 private List<Artwork> read(String query,List<UUID> ids,int limit){
  if(query==null)query="";if(query.length()>200||ids==null||ids.size()>5||limit<1||limit>10)throw AssistantFailure.invalid("Artwork query is invalid");
  if(query.isBlank()&&ids.isEmpty())return List.of();
  var params=new MapSqlParameterSource("query",query.strip()).addValue("fields",FIELDS).addValue("limit",limit);
  String publicJoin=" FROM catalog_annotation_revisions ar JOIN catalog_annotation_field_definitions fd ON fd.batch_id=ar.batch_id AND fd.revision=ar.revision AND fd.annotation_file_id=ar.annotation_file_id AND fd.field_key=ar.field_key WHERE ar.batch_id=sr.batch_id AND ar.revision=sr.revision AND ar.annotation_file_id=sr.annotation_file_id AND ar.record_number=sr.record_number AND fd.visibility='public' AND ar.field_key IN (:fields)";
  String sql="SELECT p.public_id,sr.batch_id,sr.revision,sr.annotation_file_id,sr.record_number,p.image_asset_id "+PostgresCatalogReadStore.CURRENT_SOURCE+PostgresCatalogReadStore.GALLERY_WHERE;
  if(!ids.isEmpty()){params.addValue("ids",ids);sql+=" AND p.public_id IN (:ids)";}
  else sql+=" AND EXISTS(SELECT 1"+publicJoin+" AND (strpos(lower(coalesce(ar.normalized_value,'')),lower(:query))>0 OR strpos(lower(coalesce(ar.raw_value,'')),lower(:query))>0))";
  sql+=" ORDER BY p.public_id LIMIT :limit";
  return jdbc.query(sql,params,(rs,n)->{
   UUID batch=rs.getObject("batch_id",UUID.class);int revision=rs.getInt("revision");
   var fp=new MapSqlParameterSource("batch",batch).addValue("revision",revision).addValue("file",rs.getObject("annotation_file_id")).addValue("record",rs.getInt("record_number")).addValue("fields",FIELDS);
   var fields=jdbc.query("SELECT ar.field_key,ar.normalized_value,ar.raw_value,ar.value_status,ar.assertion_status FROM catalog_annotation_revisions ar JOIN catalog_annotation_field_definitions fd ON fd.batch_id=ar.batch_id AND fd.revision=ar.revision AND fd.annotation_file_id=ar.annotation_file_id AND fd.field_key=ar.field_key WHERE ar.batch_id=:batch AND ar.revision=:revision AND ar.annotation_file_id=:file AND ar.record_number=:record AND fd.visibility='public' AND ar.field_key IN (:fields) ORDER BY ar.field_key LIMIT 50",fp,(f,i)->new Field(f.getString(1),bounded(f.getString(2)),bounded(f.getString(3)),f.getString(4),f.getString(5)));
   return new Artwork(rs.getObject("public_id",UUID.class),batch,revision,rs.getObject("image_asset_id",UUID.class),rs.getString("annotation_file_id"),rs.getInt("record_number"),PROJECTION,fields);
  });
 }
 public Artwork require(UUID id,UUID batch,int revision){var list=search("",List.of(id),1);if(list.isEmpty())throw AssistantFailure.notFound();var value=list.get(0);if(!value.batchId().equals(batch)||value.revision()!=revision)throw new AssistantFailure(409,"ASSISTANT_ARTWORK_REVISION_CHANGED","Artwork revision changed; select the current public version");return value;}
 private static String bounded(String value){return value==null?null:value.substring(0,Math.min(2000,value.length()));}
}

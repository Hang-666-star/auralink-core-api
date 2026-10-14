package com.auralink.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Local maintenance entry point, invoked from verified extracted Core bytes by the existing operational owner.
 * No network admin endpoint, schema migration, scheduler, provider dispatch or arbitrary SQL. */
public final class AssistantStorageMaintenance {
 public static void main(String[] args) {
  try { execute(args); }
  catch(Exception failure){System.err.println("{\"status\":\"REFUSED\",\"reason\":\"Assistant maintenance configuration, database or namespace validation failed; no credentials are reported\"}");System.exit(2);}
 }
 private static void execute(String[] args) throws Exception {
  if(args.length!=3||!List.of("--dry-run","--apply").contains(args[1]))throw new IllegalArgumentException("Usage: AssistantStorageMaintenance PRIVATE_CONFIG.json --dry-run|--apply LIMIT");
  Path file=Path.of(args[0]).toAbsolutePath();
  if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536||Files.getPosixFilePermissions(file).stream().anyMatch(p->p.name().startsWith("GROUP_")||p.name().startsWith("OTHERS_")))throw new IllegalArgumentException("Maintenance configuration must be a private regular file");
  var json=new ObjectMapper().findAndRegisterModules();Map<String,String> config=json.readValue(Files.readAllBytes(file),new TypeReference<>(){});
  int limit=Integer.parseInt(args[2]);if(limit<1||limit>100)throw new IllegalArgumentException("Limit must be 1..100");
  var policy=AssistantStoragePolicy.from(config);policy.requireCleanup();
  String url=config.get("jdbcUrl"),root=config.get("attachmentRoot");
  if(url==null||!url.startsWith("jdbc:postgresql:")||root==null)throw new IllegalArgumentException("Explicit PostgreSQL and private Assistant root are required");
  var source=new DriverManagerDataSource(url,config.get("jdbcUser"),config.get("jdbcPassword"));
  var clock=Clock.systemUTC();var store=new PostgresAssistantStore(source,clock,Duration.ofDays(7),8,policy);store.verifySchema();
  // Refuse to initialize any missing namespace: application installation owns creation.
  Path storageRoot=Path.of(root);if(!Files.isRegularFile(storageRoot.resolve(".assistant-attachments-v1"),LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Existing Assistant namespace is required");
  var storage=new AssistantAttachmentStorage(storageRoot,10*1024*1024,32_000_000);
  System.out.println(json.writeValueAsString(new AssistantAttachmentService(store,storage,clock).maintain(args[1].equals("--dry-run"),limit)));
 }
}

package com.auralink.workflow.snapshot;

import com.auralink.workflow.graph.CanonicalWorkflowGraph;
import com.auralink.workflow.graph.WorkflowGraphCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WorkflowSnapshotFactory {
   public static final int SNAPSHOT_VERSION = 1;
   private final WorkflowGraphCodec graphCodec;
   private final ObjectMapper canonicalMapper;

   public WorkflowSnapshotFactory(WorkflowGraphCodec graphCodec) {
      this.graphCodec = graphCodec;
      this.canonicalMapper = graphCodec.canonicalMapperCopy();
   }

   public WorkflowSnapshotResult create(String workflowId, String workflowName, int workflowSchemaVersion, CanonicalWorkflowGraph graph) {
      CanonicalWorkflowGraph detachedGraph = this.graphCodec.decode(this.graphCodec.encode(graph));
      WorkflowSnapshot snapshot = new WorkflowSnapshot(1, workflowId, workflowName, workflowSchemaVersion, detachedGraph);

      try {
         return new WorkflowSnapshotResult(snapshot, this.canonicalMapper.writeValueAsString(snapshot));
      } catch (JsonProcessingException exception) {
         throw new IllegalStateException("Unable to serialize workflow snapshot", exception);
      }
   }
}

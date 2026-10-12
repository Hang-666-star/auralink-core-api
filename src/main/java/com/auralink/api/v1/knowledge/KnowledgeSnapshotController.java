package com.auralink.api.v1.knowledge;

import com.auralink.api.v1.error.ApiErrorCode;
import com.auralink.api.v1.error.ApiV1Exception;
import com.auralink.service.knowledge.KnowledgeSnapshotService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSnapshotController {
   private final KnowledgeSnapshotService snapshots;
   public KnowledgeSnapshotController(KnowledgeSnapshotService snapshots) { this.snapshots = snapshots; }

   @GetMapping("/poetry-graph")
   public ResponseEntity<PoetryGraphResponse> graph(@RequestParam Map<String, String> parameters) {
      noParameters(parameters);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.graph());
   }

   @GetMapping("/china-map")
   public ResponseEntity<ChinaMapResponse> map(@RequestParam Map<String, String> parameters) {
      noParameters(parameters);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.china());
   }

   private static void noParameters(Map<String, String> parameters) {
      if (!parameters.isEmpty()) throw new ApiV1Exception(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PAINTING_QUERY,
         "此接口不接受查询参数。");
   }
}

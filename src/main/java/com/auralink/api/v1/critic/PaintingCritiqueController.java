package com.auralink.api.v1.critic;

import com.auralink.catalogcritic.PostgresPaintingCritiqueService;
import com.auralink.critic.service.PaintingCritiqueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaintingCritiqueController {
   private final PaintingCritiqueService service;
   private final ObjectMapper mapper;
   private final ObjectProvider<PostgresPaintingCritiqueService> postgresService;

   public PaintingCritiqueController(PaintingCritiqueService service, ObjectMapper mapper, ObjectProvider<PostgresPaintingCritiqueService> postgresService) {
      this.service = service;
      this.mapper = mapper;
      this.postgresService = postgresService;
   }

   @GetMapping("/critic/capability")
   public PaintingCritiqueCapabilityResponse capability() {
      PostgresPaintingCritiqueService postgres = (PostgresPaintingCritiqueService)this.postgresService.getIfAvailable();
      return PaintingCritiqueCapabilityResponse.from(postgres == null ? this.service.submissionAvailable() : postgres.submissionAvailable());
   }

   @PostMapping("/paintings/{paintingId}/critic")
   public ResponseEntity<PaintingCritiqueTaskResponse> submit(
      @PathVariable String paintingId, @RequestBody(required = false) PaintingCritiqueSubmissionRequest request
   ) {
      PostgresPaintingCritiqueService postgres = (PostgresPaintingCritiqueService)this.postgresService.getIfAvailable();
      if (postgres != null) {
         PostgresPaintingCritiqueService.Submission submission = postgres.submit(paintingId, request == null ? null : request.profile());
         return ResponseEntity.status(submission.reusedSuccess() ? HttpStatus.OK : HttpStatus.ACCEPTED)
            .body(PaintingCritiqueTaskResponse.from(submission.task(), submission.reusedSuccess(), this.mapper));
      } else {
         PaintingCritiqueService.Submission submission = this.service.submit(paintingId, request == null ? null : request.profile());
         return ResponseEntity.status(submission.reusedSuccess() ? HttpStatus.OK : HttpStatus.ACCEPTED)
            .body(PaintingCritiqueTaskResponse.from(submission.task(), submission.reusedSuccess(), this.mapper));
      }
   }

   @GetMapping("/paintings/{paintingId}/critic")
   public PaintingCritiqueTaskResponse current(@PathVariable String paintingId) {
      PostgresPaintingCritiqueService postgres = (PostgresPaintingCritiqueService)this.postgresService.getIfAvailable();
      return postgres == null
         ? PaintingCritiqueTaskResponse.from(this.service.current(paintingId), false, this.mapper)
         : PaintingCritiqueTaskResponse.from(postgres.current(paintingId), false, this.mapper);
   }

   @GetMapping("/critic-tasks/{critiqueId}")
   public PaintingCritiqueTaskResponse task(@PathVariable String critiqueId) {
      PostgresPaintingCritiqueService postgres = (PostgresPaintingCritiqueService)this.postgresService.getIfAvailable();
      return postgres == null
         ? PaintingCritiqueTaskResponse.from(this.service.get(critiqueId), false, this.mapper)
         : PaintingCritiqueTaskResponse.from(postgres.get(critiqueId), false, this.mapper);
   }
}

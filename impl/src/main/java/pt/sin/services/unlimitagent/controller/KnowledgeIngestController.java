package pt.sin.services.unlimitagent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.sin.services.unlimitagent.api.KnowledgeBaseAdminApi;
import pt.sin.services.unlimitagent.model.KnowledgeIngestRequest;
import pt.sin.services.unlimitagent.service.KnowledgeService;

@RestController
public class KnowledgeIngestController implements KnowledgeBaseAdminApi {

    private final KnowledgeService knowledgeService;

    public KnowledgeIngestController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public ResponseEntity<Void> ingest(KnowledgeIngestRequest knowledgeIngestRequest) {
        knowledgeService.ingest(knowledgeIngestRequest.getIncidentId(), knowledgeIngestRequest.getText());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

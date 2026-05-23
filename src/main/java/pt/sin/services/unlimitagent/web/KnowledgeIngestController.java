package pt.sin.services.unlimitagent.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.sin.services.unlimitagent.knowledge.KnowledgeBase;
import pt.sin.services.unlimitagent.model.KnowledgeIngestRequest;

@RestController
@RequestMapping("/api/v1/admin/incidents")
public class KnowledgeIngestController {

    private final KnowledgeBase knowledgeBase;

    public KnowledgeIngestController(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @PostMapping("/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public void ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        knowledgeBase.addIncident(request.incidentId(), request.text());
    }
}

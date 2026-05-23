package pt.sin.services.unlimitagent.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.sin.services.unlimitagent.agent.IncidentAgentPipeline;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.IncidentRequest;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentAgentPipeline pipeline;

    public IncidentController(IncidentAgentPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/analyze")
    public ResponseEntity<IncidentAnalysis> analyze(@Valid @RequestBody IncidentRequest request) {
        return ResponseEntity.ok(pipeline.analyze(request));
    }
}

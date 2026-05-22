package com.example.unlimitagent.web;

import com.example.unlimitagent.agent.IncidentAgentPipeline;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.IncidentRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IncidentController {

    private final IncidentAgentPipeline pipeline;

    public IncidentController(IncidentAgentPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/incidents/analyze")
    public IncidentAnalysis analyze(@RequestBody @Valid IncidentRequest request) {
        return pipeline.analyze(request);
    }
}

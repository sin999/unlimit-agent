package pt.sin.services.unlimitagent.service;

import org.springframework.stereotype.Service;
import pt.sin.services.unlimitagent.agent.IncidentAgentPipeline;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.IncidentRequest;

@Service
public class IncidentService {

    private final IncidentAgentPipeline pipeline;

    public IncidentService(IncidentAgentPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public IncidentAnalysis analyze(IncidentRequest request) {
        return pipeline.analyze(request);
    }
}

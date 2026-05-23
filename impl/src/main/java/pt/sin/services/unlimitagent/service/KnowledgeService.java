package pt.sin.services.unlimitagent.service;

import org.springframework.stereotype.Service;
import pt.sin.services.unlimitagent.repository.IncidentRepository;

@Service
public class KnowledgeService {

    private final IncidentRepository repository;

    public KnowledgeService(IncidentRepository repository) {
        this.repository = repository;
    }

    public void ingest(String incidentId, String text) {
        repository.save(incidentId, text);
    }
}

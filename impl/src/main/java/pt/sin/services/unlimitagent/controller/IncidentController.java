package pt.sin.services.unlimitagent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.sin.services.unlimitagent.api.IncidentsApi;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.IncidentRequest;
import pt.sin.services.unlimitagent.service.IncidentService;

@RestController
public class IncidentController implements IncidentsApi {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Override
    public ResponseEntity<IncidentAnalysis> analyze(IncidentRequest incidentRequest) {
        return ResponseEntity.ok(incidentService.analyze(incidentRequest));
    }
}

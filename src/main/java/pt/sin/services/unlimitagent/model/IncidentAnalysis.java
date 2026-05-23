package pt.sin.services.unlimitagent.model;

import java.util.List;

public record IncidentAnalysis(
        String category,
        String summary,
        Severity severity,
        List<Hypothesis> hypotheses
) {}

package com.example.unlimitagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentAnalysis(
        String category,
        String summary,
        Severity severity,
        List<Hypothesis> hypotheses
) {
}

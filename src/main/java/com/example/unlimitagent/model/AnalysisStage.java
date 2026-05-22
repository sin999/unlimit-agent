package com.example.unlimitagent.model;

public enum AnalysisStage {
    PARSE_INPUT("Parsing input"),
    ENRICH_WITH_CONTEXT("Enriching with context"),
    GENERATE_RESPONSE("Generating response");

    private final String label;

    AnalysisStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

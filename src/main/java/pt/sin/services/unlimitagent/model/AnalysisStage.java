package pt.sin.services.unlimitagent.model;

public enum AnalysisStage {
    EXTRACT_FACTS("Extracting facts"),
    ENRICH_CONTEXT("Enriching context"),
    SYNTHESIZE("Synthesizing analysis");

    private final String label;

    AnalysisStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package pt.sin.services.unlimitagent.agent;

import pt.sin.services.unlimitagent.model.AnalysisStage;

public class AgentPipelineException extends RuntimeException {

    private final AnalysisStage stage;

    public AgentPipelineException(AnalysisStage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public AnalysisStage getStage() {
        return stage;
    }
}

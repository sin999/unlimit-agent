package com.example.unlimitagent.agent;

import com.example.unlimitagent.model.AnalysisStage;

public class AgentPipelineException extends RuntimeException {

    private final AnalysisStage failedStage;

    public AgentPipelineException(AnalysisStage stage, String message, Throwable cause) {
        super("Agent pipeline failed at stage [" + stage.getLabel() + "]: " + message, cause);
        this.failedStage = stage;
    }

    public AnalysisStage getFailedStage() {
        return failedStage;
    }
}

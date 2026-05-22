package com.example.unlimitagent.agent;

import com.example.unlimitagent.client.LlmClient;
import com.example.unlimitagent.knowledge.KnowledgeBase;
import com.example.unlimitagent.model.AnalysisStage;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.IncidentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IncidentAgentPipeline {

    private static final Logger log = LoggerFactory.getLogger(IncidentAgentPipeline.class);

    private final LlmClient llmClient;
    private final KnowledgeBase knowledgeBase;
    private final ResponseParser responseParser;

    @Value("${agent.retry.max-attempts}")
    private int maxAttempts;

    @Value("${agent.retry.backoff-delay-ms}")
    private long backoffDelayMs;

    public IncidentAgentPipeline(LlmClient llmClient,
                                  KnowledgeBase knowledgeBase,
                                  ResponseParser responseParser) {
        this.llmClient = llmClient;
        this.knowledgeBase = knowledgeBase;
        this.responseParser = responseParser;
    }

    public IncidentAnalysis analyze(IncidentRequest request) {
        String stage1Output = runStage1(request.description());
        String stage2Output = runStage2(stage1Output);
        String stage3UserMessage = buildStage3UserMessage(stage1Output, stage2Output);
        return runStage3WithRetry(stage3UserMessage);
    }

    private String runStage1(String description) {
        try {
            String output = llmClient.complete(PromptTemplates.STAGE1_SYSTEM, description);
            log.debug("Stage PARSE_INPUT completed: {}", output);
            return output;
        } catch (Exception e) {
            throw new AgentPipelineException(AnalysisStage.PARSE_INPUT, e.getMessage(), e);
        }
    }

    private String runStage2(String stage1Output) {
        try {
            String userMessage = PromptTemplates.STAGE2_USER_TEMPLATE
                    .replace("<stage1_output>", stage1Output)
                    .replace("<system_description>", knowledgeBase.getSystemDescription())
                    .replace("<past_incidents>", knowledgeBase.getPastIncidents());
            String output = llmClient.complete(PromptTemplates.STAGE2_SYSTEM, userMessage);
            log.debug("Stage ENRICH_WITH_CONTEXT completed: {}", output);
            return output;
        } catch (Exception e) {
            throw new AgentPipelineException(AnalysisStage.ENRICH_WITH_CONTEXT, e.getMessage(), e);
        }
    }

    private String buildStage3UserMessage(String stage1Output, String stage2Output) {
        return PromptTemplates.STAGE3_USER_TEMPLATE
                .replace("<stage1_output>", stage1Output)
                .replace("<stage2_output>", stage2Output);
    }

    private IncidentAnalysis runStage3WithRetry(String stage3UserMessage) {
        String stage3Output;
        try {
            stage3Output = llmClient.complete(PromptTemplates.STAGE3_SYSTEM, stage3UserMessage);
            log.debug("Stage GENERATE_RESPONSE completed: {}", stage3Output);
        } catch (Exception e) {
            throw new AgentPipelineException(AnalysisStage.GENERATE_RESPONSE, e.getMessage(), e);
        }

        String currentUserMessage = stage3UserMessage;
        int attempt = 1;
        while (true) {
            try {
                return responseParser.parse(stage3Output);
            } catch (ResponseParseException e) {
                if (attempt >= maxAttempts) {
                    throw new AgentPipelineException(AnalysisStage.GENERATE_RESPONSE, e.getMessage(), e);
                }
                String refinedUserMessage = currentUserMessage
                        + PromptTemplates.RETRY_SUFFIX_TEMPLATE.replace("<validation_errors>", e.getMessage());
                try {
                    stage3Output = llmClient.complete(PromptTemplates.STAGE3_SYSTEM, refinedUserMessage);
                } catch (Exception retryEx) {
                    throw new AgentPipelineException(AnalysisStage.GENERATE_RESPONSE, retryEx.getMessage(), retryEx);
                }
                attempt++;
                sleep();
                log.warn("Stage GENERATE_RESPONSE parse failed (attempt {}), retrying: {}", attempt - 1, e.getMessage());
                currentUserMessage = refinedUserMessage;
            }
        }
    }

    private void sleep() {
        if (backoffDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffDelayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

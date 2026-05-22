package com.example.unlimitagent.agent;

import com.example.unlimitagent.client.LlmApiException;
import com.example.unlimitagent.knowledge.KnowledgeBase;
import com.example.unlimitagent.model.AnalysisStage;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.IncidentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IncidentAgentPipeline {

    private static final Logger log = LoggerFactory.getLogger(IncidentAgentPipeline.class);

    private final ChatClient chatClient;
    private final KnowledgeBase knowledgeBase;
    private final ResponseParser responseParser;

    @Value("${agent.retry.max-attempts}")
    private int maxAttempts;

    @Value("${agent.retry.backoff-delay-ms}")
    private long backoffDelayMs;

    public IncidentAgentPipeline(ChatClient chatClient,
                                  KnowledgeBase knowledgeBase,
                                  ResponseParser responseParser) {
        this.chatClient = chatClient;
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
            String output = chatClient.prompt()
                    .system(PromptTemplates.STAGE1_SYSTEM)
                    .user(description)
                    .call()
                    .content();
            log.debug("Stage PARSE_INPUT completed: {}", output);
            return output;
        } catch (NonTransientAiException | TransientAiException e) {
            throw new AgentPipelineException(AnalysisStage.PARSE_INPUT, e.getMessage(),
                    new LlmApiException(0, e.getMessage()));
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

            String output = chatClient.prompt()
                    .system(PromptTemplates.STAGE2_SYSTEM)
                    .user(userMessage)
                    .call()
                    .content();
            log.debug("Stage ENRICH_WITH_CONTEXT completed: {}", output);
            return output;
        } catch (NonTransientAiException | TransientAiException e) {
            throw new AgentPipelineException(AnalysisStage.ENRICH_WITH_CONTEXT,
                    e.getMessage(), new LlmApiException(0, e.getMessage()));
        } catch (Exception e) {
            throw new AgentPipelineException(AnalysisStage.ENRICH_WITH_CONTEXT, e.getMessage(), e);
        }
    }

    private String buildStage3UserMessage(String stage1Output, String stage2Output) {
        return PromptTemplates.STAGE3_USER_TEMPLATE
                .replace("<stage1_output>", stage1Output)
                .replace("<stage2_output>", stage2Output);
    }

    private IncidentAnalysis runStage3WithRetry(String initialStage3UserMessage) {
        String stage3UserMessage = initialStage3UserMessage;
        int attempt = 1;

        while (true) {
            try {
                if (attempt == 1) {
                    // First attempt: use entity() for structured output via BeanOutputConverter
                    IncidentAnalysis result = chatClient.prompt()
                            .system(PromptTemplates.STAGE3_SYSTEM)
                            .user(stage3UserMessage)
                            .call()
                            .entity(IncidentAnalysis.class);
                    responseParser.validateAnalysis(result);
                    log.debug("Stage GENERATE_RESPONSE completed on attempt {}", attempt);
                    return result;
                } else {
                    // Subsequent attempts: fall back to raw content + manual parse
                    String rawOutput = chatClient.prompt()
                            .system(PromptTemplates.STAGE3_SYSTEM)
                            .user(stage3UserMessage)
                            .call()
                            .content();
                    IncidentAnalysis result = responseParser.parseRaw(rawOutput);
                    log.debug("Stage GENERATE_RESPONSE completed on attempt {}", attempt);
                    return result;
                }
            } catch (ResponseParseException e) {
                if (attempt >= maxAttempts) {
                    throw new AgentPipelineException(AnalysisStage.GENERATE_RESPONSE, e.getMessage(), e);
                }
                log.warn("Stage GENERATE_RESPONSE failed (attempt {}), retrying: {}", attempt, e.getMessage());
                sleep();
                stage3UserMessage = stage3UserMessage
                        + PromptTemplates.RETRY_SUFFIX_TEMPLATE
                                .replace("<validation_errors>", e.getMessage());
                attempt++;
            } catch (RuntimeException e) {
                // Covers entity() parse failures (RuntimeException wrapping JsonProcessingException)
                // and Spring AI provider exceptions
                if (e instanceof NonTransientAiException || e instanceof TransientAiException) {
                    throw new AgentPipelineException(AnalysisStage.GENERATE_RESPONSE,
                            e.getMessage(), new LlmApiException(0, e.getMessage()));
                }
                // Treat as parse failure and retry
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (attempt >= maxAttempts) {
                    throw new AgentPipelineException(AnalysisStage.GENERATE_RESPONSE, errorMsg, e);
                }
                log.warn("Stage GENERATE_RESPONSE entity parse failed (attempt {}), retrying: {}", attempt, errorMsg);
                sleep();
                stage3UserMessage = stage3UserMessage
                        + PromptTemplates.RETRY_SUFFIX_TEMPLATE
                                .replace("<validation_errors>", errorMsg);
                attempt++;
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

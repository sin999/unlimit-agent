package pt.sin.services.unlimitagent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.sin.services.unlimitagent.knowledge.KnowledgeBase;
import pt.sin.services.unlimitagent.model.AnalysisStage;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.IncidentRequest;

import static java.util.Objects.isNull;

@Service
public class IncidentAgentPipeline {

    private static final Logger log = LoggerFactory.getLogger(IncidentAgentPipeline.class);
    private static final int MIN_ATTEMPTS = 2;

    private final ChatClient chatClient;
    private final KnowledgeBase knowledgeBase;
    private final ResponseParser responseParser;
    private final PromptTemplates prompts;
    private final int maxAttempts;
    private final long backoffDelayMs;

    public IncidentAgentPipeline(
            ChatClient chatClient,
            KnowledgeBase knowledgeBase,
            ResponseParser responseParser,
            PromptTemplates prompts,
            @Value("${agent.retry.max-attempts:3}") int maxAttempts,
            @Value("${agent.retry.backoff-delay-ms:500}") long backoffDelayMs) {
        if (maxAttempts < MIN_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "agent.retry.max-attempts must be >= " + MIN_ATTEMPTS + ", got: " + maxAttempts);
        }
        this.chatClient = chatClient;
        this.knowledgeBase = knowledgeBase;
        this.responseParser = responseParser;
        this.prompts = prompts;
        this.maxAttempts = maxAttempts;
        this.backoffDelayMs = backoffDelayMs;
    }

    public IncidentAnalysis analyze(IncidentRequest request) {
        String stage1Output = runStage1(request.description());
        log.debug("Stage 1 complete");
        String stage2Output = runStage2(stage1Output, request.description());
        log.debug("Stage 2 complete");
        IncidentAnalysis result = runStage3WithRetry(stage1Output, stage2Output);
        log.debug("Stage 3 complete");
        return result;
    }

    private String runStage1(String description) {
        try {
            return chatClient.prompt()
                    .system(prompts.getStage1System())
                    .user(description)
                    .call()
                    .content();
        } catch (Exception e) {
            throw new AgentPipelineException(
                    AnalysisStage.EXTRACT_FACTS, "Stage 1 failed: " + e.getMessage(), e);
        }
    }

    private String runStage2(String stage1Output, String originalDescription) {
        try {
            String userMessage = prompts.renderStage2UserMessage(
                    stage1Output,
                    knowledgeBase.getSystemDescription(),
                    knowledgeBase.getPastIncidents(originalDescription));
            return chatClient.prompt()
                    .system(prompts.getStage2System())
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception e) {
            throw new AgentPipelineException(
                    AnalysisStage.ENRICH_CONTEXT, "Stage 2 failed: " + e.getMessage(), e);
        }
    }

    private IncidentAnalysis runStage3WithRetry(String stage1Output, String stage2Output) {
        String originalUserMessage = prompts.renderStage3UserMessage(stage1Output, stage2Output);
        String userMessage = originalUserMessage;
        int attempt = 1;

        while (true) {
            try {
                return callStage3(userMessage, attempt);
            } catch (ResponseParseException e) {
                if (attempt >= maxAttempts) {
                    throw new AgentPipelineException(
                            AnalysisStage.SYNTHESIZE,
                            "Stage 3 exhausted " + attempt + " attempts: " + e.getMessage(), e);
                }
                log.warn("Stage 3 attempt {} failed: {}", attempt, e.getMessage());
                sleep();
                userMessage = originalUserMessage + prompts.renderRetrySuffix(e.getMessage());
                attempt++;
            } catch (AgentPipelineException e) {
                throw e;
            } catch (Exception e) {
                throw new AgentPipelineException(
                        AnalysisStage.SYNTHESIZE, "Stage 3 LLM error: " + e.getMessage(), e);
            }
        }
    }

    private IncidentAnalysis callStage3(String userMessage, int attempt) {
        if (attempt == 1) {
            IncidentAnalysis result;
            try {
                result = chatClient.prompt()
                        .system(prompts.getStage3System())
                        .user(userMessage)
                        .call()
                        .entity(IncidentAnalysis.class);
            } catch (Exception e) {
                // entity() failed (parse or network); treat as retryable parse error
                throw new ResponseParseException("entity() call failed: " + e.getMessage(), e);
            }
            if (result == null) {
                throw new ResponseParseException("LLM returned null response");
            }
            responseParser.validateAnalysis(result);
            return result;
        } else {
            String raw = chatClient.prompt()
                    .system(prompts.getStage3System())
                    .user(userMessage)
                    .call()
                    .content();
            return isNull(raw)? null : responseParser.parseRaw(raw);
        }
    }

    private void sleep() {
        if (backoffDelayMs > 0) {
            try {
                Thread.sleep(backoffDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

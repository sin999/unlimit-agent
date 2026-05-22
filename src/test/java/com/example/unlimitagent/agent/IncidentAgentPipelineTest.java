package com.example.unlimitagent.agent;

import com.example.unlimitagent.client.LlmApiException;
import com.example.unlimitagent.client.LlmClient;
import com.example.unlimitagent.knowledge.KnowledgeBase;
import com.example.unlimitagent.model.AnalysisStage;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.IncidentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentAgentPipelineTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private KnowledgeBase knowledgeBase;

    private IncidentAgentPipeline pipeline;

    @BeforeEach
    void setUp() {
        ResponseParser responseParser = new ResponseParser(new ObjectMapper());
        pipeline = new IncidentAgentPipeline(llmClient, knowledgeBase, responseParser);
        ReflectionTestUtils.setField(pipeline, "maxAttempts", 3);
        ReflectionTestUtils.setField(pipeline, "backoffDelayMs", 0L);

        lenient().when(knowledgeBase.getSystemDescription()).thenReturn("system description");
        lenient().when(knowledgeBase.getPastIncidents()).thenReturn("past incidents");
    }

    @Test
    void returnsAnalysisOnHappyPath() {
        when(llmClient.complete(eq(PromptTemplates.STAGE1_SYSTEM), anyString()))
                .thenReturn(stage1Json());
        when(llmClient.complete(eq(PromptTemplates.STAGE2_SYSTEM), anyString()))
                .thenReturn(stage2Json());
        when(llmClient.complete(eq(PromptTemplates.STAGE3_SYSTEM), anyString()))
                .thenReturn(validAnalysisJson());

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("Card payments are failing"));
        assertThat(result.category()).isEqualTo("External payment provider issue");
    }

    @Test
    void retriesOnParseFailureAndSucceeds() {
        when(llmClient.complete(eq(PromptTemplates.STAGE1_SYSTEM), anyString())).thenReturn(stage1Json());
        when(llmClient.complete(eq(PromptTemplates.STAGE2_SYSTEM), anyString())).thenReturn(stage2Json());

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.complete(eq(PromptTemplates.STAGE3_SYSTEM), userMessageCaptor.capture()))
                .thenReturn("not valid json")
                .thenReturn(validAnalysisJson());

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("Something is broken"));
        assertThat(result).isNotNull();

        verify(llmClient, times(2)).complete(eq(PromptTemplates.STAGE3_SYSTEM), anyString());

        String retryMessage = userMessageCaptor.getAllValues().get(1);
        assertThat(retryMessage).containsIgnoringCase("validation");
    }

    @Test
    void throwsAfterMaxRetries() {
        when(llmClient.complete(eq(PromptTemplates.STAGE1_SYSTEM), anyString())).thenReturn(stage1Json());
        when(llmClient.complete(eq(PromptTemplates.STAGE2_SYSTEM), anyString())).thenReturn(stage2Json());
        when(llmClient.complete(eq(PromptTemplates.STAGE3_SYSTEM), anyString())).thenReturn("not valid json");

        assertThatThrownBy(() -> pipeline.analyze(new IncidentRequest("Something is broken")))
                .isInstanceOf(AgentPipelineException.class);
    }

    @Test
    void throwsOnStage1Failure() {
        when(llmClient.complete(eq(PromptTemplates.STAGE1_SYSTEM), anyString()))
                .thenThrow(new LlmApiException(500, "internal error"));

        assertThatThrownBy(() -> pipeline.analyze(new IncidentRequest("Something is broken")))
                .isInstanceOf(AgentPipelineException.class)
                .satisfies(e -> assertThat(((AgentPipelineException) e).getFailedStage())
                        .isEqualTo(AnalysisStage.PARSE_INPUT));
    }

    private String stage1Json() {
        return "{\"affected_services\":[\"payment-service\"],\"symptoms\":[\"timeouts\"],\"error_types\":[\"timeout\"],\"time_context\":null}";
    }

    private String stage2Json() {
        return "{\"matched_past_incident\":\"INC-101\",\"likely_category\":\"External payment provider issue\",\"relevant_services\":[\"payment-service\"],\"context_notes\":\"matches INC-101\"}";
    }

    private String validAnalysisJson() {
        return """
                {
                  "category": "External payment provider issue",
                  "summary": "PayGate is not responding, causing card payment failures.",
                  "severity": "high",
                  "hypotheses": [
                    {
                      "title": "PayGate degradation",
                      "reasoning": "Timeouts only on PayGate",
                      "next_steps": ["Check PayGate status", "Review error logs"]
                    }
                  ]
                }
                """;
    }
}

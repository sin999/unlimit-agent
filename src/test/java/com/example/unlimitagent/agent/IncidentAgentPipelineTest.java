package com.example.unlimitagent.agent;

import com.example.unlimitagent.client.LlmApiException;
import com.example.unlimitagent.knowledge.KnowledgeBase;
import com.example.unlimitagent.model.AnalysisStage;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.IncidentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentAgentPipelineTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private KnowledgeBase knowledgeBase;

    private IncidentAgentPipeline pipeline;

    @BeforeEach
    void setUp() {
        ResponseParser responseParser = new ResponseParser(new ObjectMapper());
        pipeline = new IncidentAgentPipeline(chatClient, knowledgeBase, responseParser);
        ReflectionTestUtils.setField(pipeline, "maxAttempts", 3);
        ReflectionTestUtils.setField(pipeline, "backoffDelayMs", 0L);

        // Default ChatClient fluent chain setup
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);

        lenient().when(knowledgeBase.getSystemDescription()).thenReturn("system description");
        lenient().when(knowledgeBase.getPastIncidents()).thenReturn("past incidents");
    }

    @Test
    void returnsAnalysisOnHappyPath() {
        when(callResponseSpec.content())
                .thenReturn(stage1Json())
                .thenReturn(stage2Json());
        when(callResponseSpec.entity(IncidentAnalysis.class))
                .thenReturn(validAnalysis());

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("Card payments are failing"));
        assertThat(result.category()).isEqualTo("External payment provider issue");
    }

    @Test
    void retriesOnParseFailureAndSucceeds() {
        when(callResponseSpec.content())
                .thenReturn(stage1Json())
                .thenReturn(stage2Json())
                .thenReturn(validAnalysisJson());
        // First entity() call returns invalid data (null hypotheses triggers validation failure)
        when(callResponseSpec.entity(IncidentAnalysis.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("Something is broken"));
        assertThat(result).isNotNull();
        assertThat(result.category()).isEqualTo("External payment provider issue");
    }

    @Test
    void retriesOnValidationFailureAndSucceeds() {
        // entity() returns an analysis that fails validation (missing hypotheses)
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis(
                "Some category", "Some summary", null, null);

        when(callResponseSpec.content())
                .thenReturn(stage1Json())
                .thenReturn(stage2Json())
                .thenReturn(validAnalysisJson());
        when(callResponseSpec.entity(IncidentAnalysis.class))
                .thenReturn(invalidAnalysis);

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("Something is broken"));
        assertThat(result).isNotNull();
        assertThat(result.severity()).isNotNull();
    }

    @Test
    void retryUserMessageContainsValidationErrors() {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis(
                "Some category", "Some summary", null, null);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);

        when(callResponseSpec.content())
                .thenReturn(stage1Json())
                .thenReturn(stage2Json())
                .thenReturn(validAnalysisJson());
        when(callResponseSpec.entity(IncidentAnalysis.class))
                .thenReturn(invalidAnalysis);

        pipeline.analyze(new IncidentRequest("Something is broken"));

        verify(requestSpec, times(4)).user(userCaptor.capture());
        // The 4th user() call (index 3) is the retry Stage 3 user message
        String retryMessage = userCaptor.getAllValues().get(3);
        assertThat(retryMessage).containsIgnoringCase("validation");
    }

    @Test
    void throwsAfterMaxRetries() {
        when(callResponseSpec.content())
                .thenReturn(stage1Json())
                .thenReturn(stage2Json())
                .thenReturn("not valid json")
                .thenReturn("not valid json")
                .thenReturn("not valid json");
        when(callResponseSpec.entity(IncidentAnalysis.class))
                .thenThrow(new RuntimeException("JSON parse error"));

        assertThatThrownBy(() -> pipeline.analyze(new IncidentRequest("Something is broken")))
                .isInstanceOf(AgentPipelineException.class);
    }

    @Test
    void throwsOnStage1Failure() {
        when(callResponseSpec.content())
                .thenThrow(new NonTransientAiException("API error"));

        assertThatThrownBy(() -> pipeline.analyze(new IncidentRequest("Something is broken")))
                .isInstanceOf(AgentPipelineException.class)
                .satisfies(e -> assertThat(((AgentPipelineException) e).getFailedStage())
                        .isEqualTo(AnalysisStage.PARSE_INPUT));
    }

    private IncidentAnalysis validAnalysis() {
        try {
            return new ObjectMapper().readValue(validAnalysisJson(), IncidentAnalysis.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String stage1Json() {
        return "{\"affected_services\":[\"payment-service\"],\"symptoms\":[\"timeouts\"],\"error_types\":[\"timeout\"],\"time_context\":null}";
    }

    private String stage2Json() {
        return "{\"matched_past_incident\":\"INC-101\",\"likely_category\":\"External payment provider issue\",\"relevant_services\":[\"payment-service\"],\"context_notes\":\"matches INC-101\"}";
    }

    private String validAnalysisJson() {
        return "{\n" +
                "  \"category\": \"External payment provider issue\",\n" +
                "  \"summary\": \"PayGate is not responding, causing card payment failures.\",\n" +
                "  \"severity\": \"high\",\n" +
                "  \"hypotheses\": [\n" +
                "    {\n" +
                "      \"title\": \"PayGate degradation\",\n" +
                "      \"reasoning\": \"Timeouts only on PayGate\",\n" +
                "      \"next_steps\": [\"Check PayGate status\", \"Review error logs\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }
}

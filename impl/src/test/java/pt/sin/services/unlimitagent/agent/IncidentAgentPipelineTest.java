package pt.sin.services.unlimitagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;
import pt.sin.services.unlimitagent.repository.IncidentRepository;
import pt.sin.services.unlimitagent.model.AnalysisStage;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.IncidentRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentAgentPipelineTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.ChatClientRequestSpec systemSpec;
    @Mock ChatClient.ChatClientRequestSpec userSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock IncidentRepository knowledgeBase;

    private ResponseParser responseParser;
    private PromptTemplates prompts;
    private IncidentAgentPipeline pipeline;

    private static final String STAGE1_MOCK =
            "{\"affected_services\":[\"payment-service\"],\"symptoms\":[\"timeouts\"]," +
            "\"error_types\":[\"timeout\"],\"time_context\":null}";
    private static final String STAGE2_MOCK =
            "{\"matched_past_incident\":\"INC-101\",\"likely_category\":\"External payment provider issue\"," +
            "\"relevant_services\":[\"payment-service\"],\"context_notes\":\"matches INC-101\"}";

    @BeforeEach
    void setUp() {
        responseParser = new ResponseParser(new ObjectMapper(), new DefaultResourceLoader());
        prompts = new PromptTemplates(new DefaultResourceLoader());

        pipeline = new IncidentAgentPipeline(
                chatClient, knowledgeBase, responseParser, prompts, 3, 0L);

        lenient().when(chatClient.prompt()).thenReturn(promptSpec);
        lenient().when(promptSpec.system(anyString())).thenReturn(systemSpec);
        lenient().when(systemSpec.user(anyString())).thenReturn(userSpec);
        lenient().when(userSpec.call()).thenReturn(callSpec);

        lenient().when(knowledgeBase.getSystemDescription()).thenReturn("System description.");
        lenient().when(knowledgeBase.findSimilarIncidents(anyString())).thenReturn("Past incidents.");
    }

    // ── A-1 ─────────────────────────────────────────────────────────────────
    @Test
    void happyPath_returnsAnalysis() {
        when(callSpec.content()).thenReturn(STAGE1_MOCK, STAGE2_MOCK);
        when(callSpec.entity(IncidentAnalysis.class))
                .thenReturn(parseValidAnalysis());

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("payments failing"));

        assertThat(result.getCategory()).isEqualTo("External payment provider issue");
        assertThat(result.getSeverity()).isNotNull();
    }

    // ── A-2 ─────────────────────────────────────────────────────────────────
    @Test
    void retryOnParseFailure_thenSucceeds_stage3CalledTwice() {
        when(callSpec.content())
                .thenReturn(STAGE1_MOCK, STAGE2_MOCK, ResponseParserTest.canonicalJson());
        when(callSpec.entity(IncidentAnalysis.class))
                .thenThrow(new RuntimeException("parse failure"));

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("payments failing"));

        assertThat(result.getCategory()).isEqualTo("External payment provider issue");
        verify(callSpec, times(3)).content();
    }

    // ── A-3 ─────────────────────────────────────────────────────────────────
    @Test
    void retryOnValidationFailure_thenSucceeds() {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis()
                .category("Test").summary("Test summary.");
        when(callSpec.entity(IncidentAnalysis.class)).thenReturn(invalidAnalysis);
        when(callSpec.content())
                .thenReturn(STAGE1_MOCK, STAGE2_MOCK, ResponseParserTest.canonicalJson());

        IncidentAnalysis result = pipeline.analyze(new IncidentRequest("payments failing"));

        assertThat(result.getSeverity()).isNotNull();
    }

    // ── A-4 ─────────────────────────────────────────────────────────────────
    @Test
    void retryMessage_containsValidationErrors() {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis()
                .category("Test").summary("Test summary.");
        when(callSpec.entity(IncidentAnalysis.class)).thenReturn(invalidAnalysis);
        when(callSpec.content())
                .thenReturn(STAGE1_MOCK, STAGE2_MOCK, ResponseParserTest.canonicalJson());

        pipeline.analyze(new IncidentRequest("payments failing"));

        verify(systemSpec, atLeastOnce()).user(argThat((String msg) ->
                msg != null && msg.toLowerCase().contains("validation")));
    }

    // ── A-5 ─────────────────────────────────────────────────────────────────
    @Test
    void exhaustsRetries_throwsPipelineException() {
        when(callSpec.entity(IncidentAnalysis.class))
                .thenThrow(new RuntimeException("parse failure"));
        when(callSpec.content())
                .thenReturn(STAGE1_MOCK, STAGE2_MOCK, "bad", "bad");

        assertThatThrownBy(() -> pipeline.analyze(new IncidentRequest("payments failing")))
                .isInstanceOf(AgentPipelineException.class)
                .extracting(e -> ((AgentPipelineException) e).getStage())
                .isEqualTo(AnalysisStage.SYNTHESIZE);
    }

    // ── A-6 ─────────────────────────────────────────────────────────────────
    @Test
    void stage1Failure_propagatesWithCorrectStage() {
        when(callSpec.content()).thenThrow(new RuntimeException("LLM unreachable"));

        assertThatThrownBy(() -> pipeline.analyze(new IncidentRequest("payments failing")))
                .isInstanceOf(AgentPipelineException.class)
                .extracting(e -> ((AgentPipelineException) e).getStage())
                .isEqualTo(AnalysisStage.EXTRACT_FACTS);
    }

    private IncidentAnalysis parseValidAnalysis() {
        try {
            return new ObjectMapper().readValue(
                    ResponseParserTest.canonicalJson(), IncidentAnalysis.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

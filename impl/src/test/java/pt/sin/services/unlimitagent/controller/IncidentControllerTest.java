package pt.sin.services.unlimitagent.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.sin.services.unlimitagent.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.sin.services.unlimitagent.agent.AgentPipelineException;
import pt.sin.services.unlimitagent.agent.ResponseParseException;
import pt.sin.services.unlimitagent.services.IncidentService;
import pt.sin.services.unlimitagent.model.AnalysisStage;
import pt.sin.services.unlimitagent.model.Hypothesis;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.Severity;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
@Import(SecurityConfig.class)
class IncidentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean IncidentService incidentService;

    private static final IncidentAnalysis VALID_ANALYSIS = new IncidentAnalysis()
            .category("External payment provider issue")
            .summary("PayGate is not responding.")
            .severity(Severity.HIGH)
            .hypotheses(List.of(new Hypothesis()
                    .title("PayGate degradation")
                    .reasoning("Timeouts only on PayGate.")
                    .nextSteps(List.of("Check status page", "Review logs"))));

    // ── C-1 ─────────────────────────────────────────────────────────────────
    @Test
    void validRequest_returns200WithCategory() throws Exception {
        when(incidentService.analyze(any())).thenReturn(VALID_ANALYSIS);

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Card payments failing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("External payment provider issue"));
    }

    // ── C-2 ─────────────────────────────────────────────────────────────────
    @Test
    void blankDescription_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── C-3 ─────────────────────────────────────────────────────────────────
    @Test
    void missingDescription_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── C-4 ─────────────────────────────────────────────────────────────────
    @Test
    void pipelineException_returns502WithAgentError() throws Exception {
        when(incidentService.analyze(any())).thenThrow(
                new AgentPipelineException(AnalysisStage.SYNTHESIZE, "failed", new RuntimeException()));

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"payments failing\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Agent pipeline error"));
    }

    // ── C-5 ─────────────────────────────────────────────────────────────────
    @Test
    void parseException_returns502WithParseError() throws Exception {
        when(incidentService.analyze(any())).thenThrow(new ResponseParseException("bad json"));

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"payments failing\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM response parse error"));
    }

    // ── C-6 ─────────────────────────────────────────────────────────────────
    @Test
    void descriptionExceeds2000Chars_returns400() throws Exception {
        String longDesc = "x".repeat(2001);
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + longDesc + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}

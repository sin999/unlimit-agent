package com.example.unlimitagent.web;

import com.example.unlimitagent.agent.AgentPipelineException;
import com.example.unlimitagent.agent.IncidentAgentPipeline;
import com.example.unlimitagent.agent.ResponseParseException;
import com.example.unlimitagent.model.AnalysisStage;
import com.example.unlimitagent.model.Hypothesis;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentAgentPipeline pipeline;

    @Test
    void returns200WithAnalysis() throws Exception {
        when(pipeline.analyze(any())).thenReturn(validAnalysis());

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Card payments are failing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").exists());
    }

    @Test
    void returns400OnBlankDescription() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void returns400OnMissingDescription() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns502OnPipelineError() throws Exception {
        when(pipeline.analyze(any())).thenThrow(
                new AgentPipelineException(AnalysisStage.PARSE_INPUT, "stage failed", null));

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Something is broken\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Agent pipeline error"));
    }

    @Test
    void returns502OnParseError() throws Exception {
        when(pipeline.analyze(any())).thenThrow(
                new ResponseParseException("parse failed", "raw output"));

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Something is broken\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM response parse error"));
    }

    private IncidentAnalysis validAnalysis() {
        return new IncidentAnalysis(
                "External payment provider issue",
                "PayGate is not responding, causing card payment failures.",
                Severity.HIGH,
                List.of(new Hypothesis(
                        "PayGate degradation",
                        "Timeouts only on PayGate",
                        List.of("Check PayGate status", "Review error logs")))
        );
    }
}

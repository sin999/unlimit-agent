package pt.sin.services.unlimitagent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.sin.services.unlimitagent.knowledge.KnowledgeBase;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KnowledgeIngestController.class)
class KnowledgeIngestControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean KnowledgeBase knowledgeBase;

    // ── I-1 ─────────────────────────────────────────────────────────────────
    @Test
    void validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":\"INC-105\",\"text\":\"Some incident text\"}"))
                .andExpect(status().isCreated());

        verify(knowledgeBase).addIncident("INC-105", "Some incident text");
    }

    // ── I-2 ─────────────────────────────────────────────────────────────────
    @Test
    void blankIncidentId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":\"\",\"text\":\"Some text\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── I-3 ─────────────────────────────────────────────────────────────────
    @Test
    void missingTextField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":\"INC-105\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}

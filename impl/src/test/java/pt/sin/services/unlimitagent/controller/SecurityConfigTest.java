package pt.sin.services.unlimitagent.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import pt.sin.services.unlimitagent.config.SecurityConfig;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.Severity;
import pt.sin.services.unlimitagent.services.IncidentService;
import pt.sin.services.unlimitagent.services.KnowledgeService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IncidentController.class, KnowledgeIngestController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.enabled=true")
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean IncidentService incidentService;
    @MockitoBean KnowledgeService knowledgeService;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final IncidentAnalysis VALID_ANALYSIS = new IncidentAnalysis()
            .category("External payment provider issue")
            .summary("PayGate is not responding.")
            .severity(Severity.HIGH)
            .hypotheses(List.of());

    // ── SEC-1 ────────────────────────────────────────────────────────────────
    // (disabled path covered by existing IncidentControllerTest / KnowledgeIngestControllerTest)

    // ── SEC-2 ────────────────────────────────────────────────────────────────
    @Test
    void userToken_analyze_returns200() throws Exception {
        when(incidentService.analyze(any())).thenReturn(VALID_ANALYSIS);

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"payments failing\"}"))
                .andExpect(status().isOk());
    }

    // ── SEC-3 ────────────────────────────────────────────────────────────────
    @Test
    void adminToken_analyze_returns200() throws Exception {
        when(incidentService.analyze(any())).thenReturn(VALID_ANALYSIS);

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"payments failing\"}"))
                .andExpect(status().isOk());
    }

    // ── SEC-4 ────────────────────────────────────────────────────────────────
    @Test
    void noToken_analyze_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"payments failing\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ── SEC-5 ────────────────────────────────────────────────────────────────
    @Test
    void userToken_ingest_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":\"INC-106\",\"text\":\"Some text\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // ── SEC-6 ────────────────────────────────────────────────────────────────
    @Test
    void adminToken_ingest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":\"INC-106\",\"text\":\"Some text\"}"))
                .andExpect(status().isCreated());
    }

    // ── SEC-7 ────────────────────────────────────────────────────────────────
    @Test
    void noToken_ingest_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incidentId\":\"INC-106\",\"text\":\"Some text\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ── SEC-8 (health endpoint) ──────────────────────────────────────────────
    // Actuator endpoints are not loaded in @WebMvcTest slices.
    // SecurityConfig.filterChain() permits /actuator/health without auth.
    // Verified by code review and manual test: curl http://localhost:8080/actuator/health
    // returns 200 regardless of SECURITY_ENABLED value.
}

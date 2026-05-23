package pt.sin.services.unlimitagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;
import pt.sin.services.unlimitagent.model.Severity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseParserTest {

    private ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser(new ObjectMapper(), new DefaultResourceLoader());
    }

    // ── P-1 ─────────────────────────────────────────────────────────────────
    @Test
    void validJson_parsesCorrectly() {
        IncidentAnalysis result = parser.parseRaw(canonicalJson());
        assertThat(result.category()).isEqualTo("External payment provider issue");
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.hypotheses()).hasSize(1);
        assertThat(result.hypotheses().get(0).nextSteps()).hasSize(2);
    }

    // ── P-2 ─────────────────────────────────────────────────────────────────
    @Test
    void markdownFences_areStripped() {
        String fenced = "```json\n" + canonicalJson() + "\n```";
        IncidentAnalysis result = parser.parseRaw(fenced);
        assertThat(result.category()).isNotBlank();
    }

    // ── P-3 ─────────────────────────────────────────────────────────────────
    @Test
    void invalidJson_throws() {
        assertThatThrownBy(() -> parser.parseRaw("not json at all"))
                .isInstanceOf(ResponseParseException.class);
    }

    // ── P-4 ─────────────────────────────────────────────────────────────────
    @Test
    void missingSeverity_throws() {
        String json = canonicalJson().replace("\"severity\": \"high\",", "");
        assertThatThrownBy(() -> parser.parseRaw(json))
                .isInstanceOf(ResponseParseException.class)
                .hasMessageContaining("severity");
    }

    // ── P-5 ─────────────────────────────────────────────────────────────────
    @Test
    void emptyHypotheses_throws() {
        String json = canonicalJson().replace(
                "\"hypotheses\": [{", "\"hypotheses\": [")
                .replace("\"title\": \"PayGate degradation\",\n      \"reasoning\": \"Timeouts only on PayGate calls; other services stable.\",\n      \"next_steps\": [\"Check PayGate status page\", \"Review payment-service error logs\"]\n    }", "");
        // Simplest: build directly with empty hypotheses
        String emptyHyp = """
                {
                  "category": "Test",
                  "summary": "Test summary.",
                  "severity": "high",
                  "hypotheses": []
                }
                """;
        assertThatThrownBy(() -> parser.parseRaw(emptyHyp))
                .isInstanceOf(ResponseParseException.class)
                .hasMessageContaining("hypotheses");
    }

    // ── P-6 ─────────────────────────────────────────────────────────────────
    @Test
    void caseInsensitiveSeverity_accepted() {
        String json = canonicalJson().replace("\"severity\": \"high\"", "\"severity\": \"HIGH\"");
        IncidentAnalysis result = parser.parseRaw(json);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    // ── P-7 ─────────────────────────────────────────────────────────────────
    @Test
    void blankCategory_throws() {
        String json = canonicalJson().replace(
                "\"category\": \"External payment provider issue\"",
                "\"category\": \"\"");
        assertThatThrownBy(() -> parser.parseRaw(json))
                .isInstanceOf(ResponseParseException.class)
                .hasMessageContaining("category");
    }

    // ── P-8 ─────────────────────────────────────────────────────────────────
    @Test
    void blankSummary_throws() {
        String json = canonicalJson().replace(
                "\"summary\": \"PayGate is not responding in time, causing mass card payment failures.\"",
                "\"summary\": \"\"");
        assertThatThrownBy(() -> parser.parseRaw(json))
                .isInstanceOf(ResponseParseException.class)
                .hasMessageContaining("summary");
    }

    static String canonicalJson() {
        return """
                {
                  "category": "External payment provider issue",
                  "summary": "PayGate is not responding in time, causing mass card payment failures.",
                  "severity": "high",
                  "hypotheses": [
                    {
                      "title": "PayGate degradation",
                      "reasoning": "Timeouts only on PayGate calls; other services stable.",
                      "next_steps": ["Check PayGate status page", "Review payment-service error logs"]
                    }
                  ]
                }
                """;
    }
}

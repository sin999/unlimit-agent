package com.example.unlimitagent.agent;

import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseParserTest {

    private ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser(new ObjectMapper());
    }

    @Test
    void parsesValidJson() {
        IncidentAnalysis result = parser.parse(validJson());
        assertThat(result.category()).isEqualTo("External payment provider issue");
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.hypotheses()).hasSize(1);
    }

    @Test
    void stripsMarkdownFences() {
        String fenced = "```json\n" + validJson() + "\n```";
        IncidentAnalysis result = parser.parse(fenced);
        assertThat(result.category()).isNotBlank();
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not json at all"))
                .isInstanceOf(ResponseParseException.class);
    }

    @Test
    void throwsOnMissingSeverity() {
        String json = """
                {
                  "category": "External payment provider issue",
                  "summary": "PayGate is not responding.",
                  "hypotheses": [
                    {
                      "title": "PayGate degradation",
                      "reasoning": "Timeouts only on PayGate",
                      "next_steps": ["Check PayGate status", "Review error logs"]
                    }
                  ]
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ResponseParseException.class);
    }

    @Test
    void throwsOnEmptyHypotheses() {
        String json = """
                {
                  "category": "External payment provider issue",
                  "summary": "PayGate is not responding.",
                  "severity": "high",
                  "hypotheses": []
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ResponseParseException.class);
    }

    @Test
    void acceptsCaseInsensitiveSeverity() {
        String json = validJson().replace("\"severity\": \"high\"", "\"severity\": \"HIGH\"");
        IncidentAnalysis result = parser.parse(json);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    private String validJson() {
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

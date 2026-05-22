package com.example.unlimitagent.agent;

import com.example.unlimitagent.model.IncidentAnalysis;
import com.example.unlimitagent.model.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        IncidentAnalysis result = parser.parseRaw(validJson());
        assertThat(result.category()).isEqualTo("External payment provider issue");
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.hypotheses()).hasSize(1);
    }

    @Test
    void stripsMarkdownFences() {
        String fenced = "```json\n" + validJson() + "\n```";
        IncidentAnalysis result = parser.parseRaw(fenced);
        assertThat(result.category()).isNotBlank();
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parseRaw("not json at all"))
                .isInstanceOf(ResponseParseException.class);
    }

    @Test
    void throwsOnMissingSeverity() {
        String json = "{\n" +
                "  \"category\": \"External payment provider issue\",\n" +
                "  \"summary\": \"PayGate is not responding.\",\n" +
                "  \"hypotheses\": [\n" +
                "    {\n" +
                "      \"title\": \"PayGate degradation\",\n" +
                "      \"reasoning\": \"Timeouts only on PayGate\",\n" +
                "      \"next_steps\": [\"Check PayGate status\", \"Review error logs\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        assertThatThrownBy(() -> parser.parseRaw(json))
                .isInstanceOf(ResponseParseException.class);
    }

    @Test
    void throwsOnEmptyHypotheses() {
        String json = "{\n" +
                "  \"category\": \"External payment provider issue\",\n" +
                "  \"summary\": \"PayGate is not responding.\",\n" +
                "  \"severity\": \"high\",\n" +
                "  \"hypotheses\": []\n" +
                "}";
        assertThatThrownBy(() -> parser.parseRaw(json))
                .isInstanceOf(ResponseParseException.class);
    }

    @Test
    void acceptsCaseInsensitiveSeverity() {
        String json = validJson().replace("\"severity\": \"high\"", "\"severity\": \"HIGH\"");
        IncidentAnalysis result = parser.parseRaw(json);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    private String validJson() {
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

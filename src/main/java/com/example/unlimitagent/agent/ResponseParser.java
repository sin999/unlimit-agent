package com.example.unlimitagent.agent;

import com.example.unlimitagent.model.Hypothesis;
import com.example.unlimitagent.model.IncidentAnalysis;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.OutputFormat;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ResponseParser {

    private static final Pattern FENCE_PATTERN =
            Pattern.compile("^```(?:json)?\\s*\\n?|\\n?```\\s*$");

    private final ObjectMapper objectMapper;

    public ResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IncidentAnalysis parse(String rawLlmOutput) {
        String cleaned = stripFences(rawLlmOutput);
        try {
            IncidentAnalysis analysis = objectMapper.readValue(cleaned, IncidentAnalysis.class);
            validateAnalysis(analysis, cleaned);
            return analysis;
        } catch (ResponseParseException e) {
            throw e;
        } catch (JacksonException e) {
            validateWithSchema(cleaned, rawLlmOutput);
            throw new ResponseParseException("JSON parse failed: " + e.getMessage(), rawLlmOutput, e);
        }
    }

    private String stripFences(String raw) {
        return FENCE_PATTERN.matcher(raw.strip()).replaceAll("").strip();
    }

    private void validateAnalysis(IncidentAnalysis analysis, String raw) {
        List<String> errors = new ArrayList<>();

        if (analysis.severity() == null) {
            errors.add("severity is null");
        }
        if (analysis.hypotheses() == null || analysis.hypotheses().isEmpty()) {
            errors.add("hypotheses is null or empty");
        } else if (analysis.hypotheses().size() > 3) {
            errors.add("hypotheses has more than 3 items");
        } else {
            for (int i = 0; i < analysis.hypotheses().size(); i++) {
                Hypothesis h = analysis.hypotheses().get(i);
                if (!StringUtils.hasText(h.title())) {
                    errors.add("hypotheses[" + i + "].title is blank");
                }
                if (!StringUtils.hasText(h.reasoning())) {
                    errors.add("hypotheses[" + i + "].reasoning is blank");
                }
                if (h.nextSteps() == null || h.nextSteps().size() < 2 || h.nextSteps().size() > 3) {
                    errors.add("hypotheses[" + i + "].next_steps must have 2-3 items");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ResponseParseException("Validation failed: " + String.join("; ", errors), raw);
        }
    }

    private void validateWithSchema(String json, String rawLlmOutput) {
        try (InputStream schemaStream = getClass().getClassLoader()
                .getResourceAsStream("schemas/incident_analysis_schema.json")) {
            if (schemaStream == null) {
                return;
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            var schema = factory.getSchema(schemaStream);
            Set<ValidationMessage> messages = schema.validate(json, InputFormat.JSON, OutputFormat.DEFAULT);
            if (!messages.isEmpty()) {
                String summary = messages.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new ResponseParseException("Schema validation failed: " + summary, rawLlmOutput);
            }
        } catch (ResponseParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseParseException("Schema validation error: " + e.getMessage(), rawLlmOutput, e);
        }
    }
}

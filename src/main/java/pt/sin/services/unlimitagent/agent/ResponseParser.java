package pt.sin.services.unlimitagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pt.sin.services.unlimitagent.model.Hypothesis;
import pt.sin.services.unlimitagent.model.IncidentAnalysis;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.networknt.schema.utils.StringUtils.isBlank;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

@Component
public class ResponseParser {

    private static final String SCHEMA_RESOURCE = "classpath:schemas/incident_analysis_schema.json";
    private static final int MAX_HYPOTHESES  = 3;
    private static final int MIN_NEXT_STEPS  = 2;
    private static final int MAX_NEXT_STEPS  = 3;

    private static final Pattern FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final JsonSchema jsonSchema;

    public ResponseParser(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.jsonSchema = loadSchema(resourceLoader);
    }

    private static JsonSchema loadSchema(ResourceLoader loader) {
        try (InputStream is = loader.getResource(SCHEMA_RESOURCE).getInputStream()) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load incident analysis JSON schema", e);
        }
    }

    public void validateAnalysis(IncidentAnalysis analysis) {
        List<String> errors = new ArrayList<>();

        if (isBlank(analysis.category())) {
            errors.add("'category' is blank or missing");
        }
        if (isBlank(analysis.summary() )) {
            errors.add("'summary' is blank or missing");
        }
        if (isNull(analysis.severity())) {
            errors.add("'severity' is missing");
        }
        if (ofNullable(analysis.hypotheses()).map(Collection::isEmpty).orElse(true)) {
            errors.add("'hypotheses' is empty or missing");
        } else {
            if (analysis.hypotheses().size() > MAX_HYPOTHESES) {
                errors.add("'hypotheses' contains more than " + MAX_HYPOTHESES + " items (" + analysis.hypotheses().size() + ")");
            }
            for (int i = 0; i < analysis.hypotheses().size(); i++) {
                Hypothesis h = analysis.hypotheses().get(i);
                if (h.title() == null || h.title().isBlank()) {
                    errors.add("hypotheses[" + i + "].title is blank");
                }
                if (h.reasoning() == null || h.reasoning().isBlank()) {
                    errors.add("hypotheses[" + i + "].reasoning is blank");
                }
                int steps = (h.nextSteps() == null) ? 0 : h.nextSteps().size();
                if (steps < MIN_NEXT_STEPS || steps > MAX_NEXT_STEPS) {
                    errors.add("hypotheses[" + i + "].next_steps must have " + MIN_NEXT_STEPS + "–" + MAX_NEXT_STEPS + " items, found " + steps);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ResponseParseException(String.join("; ", errors));
        }
    }

    public IncidentAnalysis parseRaw(String raw) {
        String stripped = stripFences(raw.trim());
        try {
            IncidentAnalysis analysis = objectMapper.readValue(stripped, IncidentAnalysis.class);
            validateAnalysis(analysis);
            return analysis;
        } catch (ResponseParseException e) {
            throw e;
        } catch (Exception jacksonEx) {
            // Jackson failed — run JSON Schema validation for richer error messages
            try {
                Set<ValidationMessage> violations = jsonSchema.validate(
                        objectMapper.readTree(stripped));
                String errors = violations.stream()
                        .map(ValidationMessage::getMessage)
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("unknown schema violation");
                throw new ResponseParseException("Schema validation failed: " + errors);
            } catch (ResponseParseException rpe) {
                throw rpe;
            } catch (Exception ignored) {
                throw new ResponseParseException(
                        "Failed to parse LLM response: " + jacksonEx.getMessage(), jacksonEx);
            }
        }
    }

    private static String stripFences(String raw) {
        Matcher matcher = FENCE_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1).trim() : raw;
    }
}

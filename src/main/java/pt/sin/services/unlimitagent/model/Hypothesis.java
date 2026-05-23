package pt.sin.services.unlimitagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Hypothesis(
        String title,
        String reasoning,
        @JsonProperty("next_steps") List<String> nextSteps
) {}

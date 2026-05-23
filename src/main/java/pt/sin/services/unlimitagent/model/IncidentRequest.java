package pt.sin.services.unlimitagent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentRequest(
        @NotBlank @Size(max = 2000) String description
) {}

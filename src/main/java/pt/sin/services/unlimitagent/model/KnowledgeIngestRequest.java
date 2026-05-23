package pt.sin.services.unlimitagent.model;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeIngestRequest(
        @NotBlank String incidentId,
        @NotBlank String text
) {}

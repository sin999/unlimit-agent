package com.example.unlimitagent.model;

import jakarta.validation.constraints.NotBlank;

public record IncidentRequest(
        @NotBlank String description
) {
}

package com.example.unlimitagent.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "llm.api")
@Validated
public record LlmProperties(
        @NotBlank String key,
        @NotBlank String baseUrl,
        @NotBlank String model,
        int maxTokens,
        String providerVersion
) {
}

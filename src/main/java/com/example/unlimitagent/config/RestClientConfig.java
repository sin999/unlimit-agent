package com.example.unlimitagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class RestClientConfig {

    @Bean("llmRestClient")
    public RestClient llmRestClient(LlmProperties props) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.key())
                .defaultHeader("x-api-key", props.key())
                .defaultHeader("Content-Type", "application/json");

        if (props.providerVersion() != null && !props.providerVersion().isBlank()) {
            builder.defaultHeader("anthropic-version", props.providerVersion());
        }

        return builder.build();
    }
}

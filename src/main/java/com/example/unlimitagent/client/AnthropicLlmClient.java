package com.example.unlimitagent.client;

import com.example.unlimitagent.config.LlmProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@Primary
public class AnthropicLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    public AnthropicLlmClient(@Qualifier("llmRestClient") RestClient restClient,
                               LlmProperties props,
                               ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userMessage);
            String responseBody = restClient.post()
                    .uri("/v1/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return extractText(responseBody);
        } catch (RestClientResponseException e) {
            throw new LlmApiException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmApiException(0, e.getMessage());
        }
    }

    private String buildRequestBody(String systemPrompt, String userMessage) throws Exception {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "max_tokens", props.maxTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );
        return objectMapper.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private String extractText(String responseBody) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new LlmApiException(0, "Empty content in LLM response");
        }
        Object text = content.get(0).get("text");
        if (text == null) {
            throw new LlmApiException(0, "No text field in LLM response content");
        }
        return text.toString();
    }
}

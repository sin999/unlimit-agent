package com.example.unlimitagent.client;

/**
 * Abstraction for LLM completion calls.
 * All pipeline logic depends on this interface only; concrete providers are swappable.
 */
public interface LlmClient {

    /**
     * Sends a completion request to the configured LLM provider.
     *
     * @param systemPrompt the system/context prompt sent to the model
     * @param userMessage  the user turn to complete
     * @return the model's text response as a raw string
     * @throws LlmApiException on any upstream error (HTTP error, network failure, etc.)
     */
    String complete(String systemPrompt, String userMessage);
}

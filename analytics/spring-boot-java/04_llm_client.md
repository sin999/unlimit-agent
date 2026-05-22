# Prompt 04 — LLM Client

**Common references:** `../common/03_llm_client_contract.md` (interface contract, config keys, design principle)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17.
LLM API is called via Spring's `RestClient` — no third-party SDK.

Config properties (already in `application.properties`):
```
llm.api.key=${LLM_API_KEY}
llm.api.base-url=${LLM_API_BASE_URL}
llm.api.model=${LLM_API_MODEL}
llm.api.max-tokens=2048
llm.api.provider-version=${LLM_API_PROVIDER_VERSION:}
```

---

## Task

### 1. `src/main/java/com/example/unlimitagent/client/LlmClient.java`

A plain Java interface with a single method:

```java
String complete(String systemPrompt, String userMessage);
```

Javadoc: describe the parameters, return value, and that `LlmApiException` is thrown on upstream errors.

### 2. `src/main/java/com/example/unlimitagent/client/LlmApiException.java`

`RuntimeException` subclass with:
- `int httpStatus`
- `String rawBody`

Constructor sets message to `"LLM API error [<httpStatus>]: <rawBody>"`.

### 3. `src/main/java/com/example/unlimitagent/config/LlmProperties.java`

`@ConfigurationProperties(prefix = "llm.api")` with fields:
- `String key`
- `String baseUrl`
- `String model`
- `int maxTokens`
- `String providerVersion`

Annotate with `@Validated`; mark `key`, `baseUrl`, and `model` with `@NotBlank`.

### 4. `src/main/java/com/example/unlimitagent/config/RestClientConfig.java`

`@Configuration` declaring a `RestClient` bean named `llmRestClient`:
- Base URL: `LlmProperties.baseUrl`
- Default headers:
  - `Authorization: Bearer <key>` (OpenAI-style)
  - `x-api-key: <key>` (Anthropic-style)
  - `content-type: application/json`
  - If `providerVersion` is not blank: `anthropic-version: <providerVersion>`

### 5. `src/main/java/com/example/unlimitagent/client/AnthropicLlmClient.java`

`@Component @Primary` implementing `LlmClient`.

`complete(String systemPrompt, String userMessage)`:
1. Build request body (see `../common/03_llm_client_contract.md` for the JSON shape).
2. `POST /v1/messages` via `llmRestClient`.
3. Extract and return `response.content[0].text`.
4. On non-2xx: throw `LlmApiException(statusCode, rawBody)`.

Use `ObjectMapper` (auto-wired) to serialise the request body.
Use inner records or `Map` for wire shapes — no separate DTO classes.
Extract a private `buildRequestBody(...)` helper if the method gets long.

---

## Output

Return all five files labelled with their paths. No explanation.

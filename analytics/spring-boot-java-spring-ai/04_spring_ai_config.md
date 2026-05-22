# Prompt 04 — Spring AI Configuration

**Common references:** `../common/03_llm_client_contract.md` (design principle, exception shape)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 3.4 / Java 17 / Spring AI 1.0.

Spring AI auto-configures a `ChatModel` bean from `application.properties`.
This prompt creates:
1. A configured `ChatClient` bean that the pipeline injects.
2. `LlmApiException` — the single exception type the pipeline throws on upstream errors
   (same contract as in `../common/03_llm_client_contract.md`).

There is **no** `LlmClient` interface or `AnthropicLlmClient` class — `ChatClient` is the
provider abstraction. Switching providers means changing the starter dependency and the
`spring.ai.*` properties — zero code changes.

---

## Task

### 1. `src/main/java/com/example/unlimitagent/config/SpringAiConfig.java`

A `@Configuration` class that produces a `ChatClient` bean:

```java
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
    }
}
```

`ChatClient.Builder` is auto-configured by the Spring AI starter using the properties in
`application.properties`. `SimpleLoggerAdvisor` logs each prompt/response at DEBUG level.

### 2. `src/main/java/com/example/unlimitagent/client/LlmApiException.java`

Same contract as in `../spring-boot-java/04_llm_client.md`:

```java
public class LlmApiException extends RuntimeException {

    private final int httpStatus;
    private final String rawBody;

    public LlmApiException(int httpStatus, String rawBody) {
        super("LLM API error [" + httpStatus + "]: " + rawBody);
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getRawBody() { return rawBody; }
}
```

The pipeline catches `org.springframework.ai.retry.NonTransientAiException` and
`org.springframework.ai.retry.TransientAiException` (Spring AI's error hierarchy) and
wraps them in `LlmApiException` so the web layer and tests stay provider-agnostic.

---

## Output

Return both files labelled with their paths. No explanation.

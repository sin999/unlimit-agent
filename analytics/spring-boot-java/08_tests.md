# Prompt 08 — Tests

**Common references:** `../common/07_test_scenarios.md` (test scenarios in plain English)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17.
Test stack: JUnit 5 + Mockito (via `spring-boot-starter-test`).

Implement all scenarios from `../common/07_test_scenarios.md` using the following
Spring/Java-specific patterns.

---

## Task

### 1. `src/test/java/com/example/unlimitagent/agent/ResponseParserTest.java`

Plain unit test — no Spring context. Instantiate `ResponseParser` directly with a real `ObjectMapper`.

Cover all scenarios from `../common/07_test_scenarios.md § Response parser tests`.

Add a private `validJson()` helper returning the canonical valid JSON string so tests
only specify their deviation.

### 2. `src/test/java/com/example/unlimitagent/agent/IncidentAgentPipelineTest.java`

`@ExtendWith(MockitoExtension.class)`. Mock `LlmClient` and `KnowledgeBase` with `@Mock`.
Inject into `IncidentAgentPipeline` via constructor or `@InjectMocks`.

Cover all scenarios from `../common/07_test_scenarios.md § Agent pipeline tests`.

For the retry scenario: use `ArgumentCaptor<String>` on the `complete()` call to assert
the second Stage 3 user-message contains the validation error text.

For the stage-1 failure scenario: `when(llmClient.complete(...)).thenThrow(new LlmApiException(500, "error"))`.
Assert `AgentPipelineException.getFailedStage() == AnalysisStage.PARSE_INPUT`.

### 3. `src/test/java/com/example/unlimitagent/web/IncidentControllerTest.java`

`@WebMvcTest(IncidentController.class)`. Mock `IncidentAgentPipeline` with `@MockBean`.

Cover all scenarios from `../common/07_test_scenarios.md § REST controller tests`.

Use `MockMvc`:
```java
mockMvc.perform(post("/api/v1/incidents/analyze")
    .contentType(APPLICATION_JSON)
    .content("{\"description\": \"...\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.category").exists());
```

For error scenarios verify both the HTTP status and the `error` field value in the response body.

---

## Output

Return all three test files with full package declarations and imports.
No explanation — just the code blocks labelled with their file paths.

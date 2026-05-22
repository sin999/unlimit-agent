# Prompt 08 — Tests (Spring AI)

**Common references:** `../common/07_test_scenarios.md` (test scenarios)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 3.4 / Java 17 / Spring AI 1.0.
Test stack: JUnit 5 + Mockito + `spring-ai-test` (provides `MockChatModel`).

The test scenarios are identical to `../common/07_test_scenarios.md`.
The Spring AI-specific differences are in **how** the LLM is mocked.

---

## Task

### 1. `src/test/java/com/example/unlimitagent/agent/ResponseParserTest.java`

Plain unit test — no Spring context.
Instantiate `ResponseParser` directly with a real `ObjectMapper`.

Cover all scenarios from `../common/07_test_scenarios.md § Response parser tests`.
No Spring AI involvement here; the logic is pure Java.

### 2. `src/test/java/com/example/unlimitagent/agent/IncidentAgentPipelineTest.java`

Use `@ExtendWith(MockitoExtension.class)`.

Mock the `ChatClient` using Spring AI's test utilities:

```java
// Option A — mock ChatClient directly with Mockito
@Mock ChatClient chatClient;
@Mock ChatClient.ChatClientRequestSpec requestSpec;
@Mock ChatClient.CallResponseSpec callSpec;
// chain: chatClient.prompt() → requestSpec.system() → requestSpec.user() → callSpec
// then: callSpec.content() or callSpec.entity(...)
```

Alternatively, use `MockChatModel` from `spring-ai-test` and build a real `ChatClient` from it:

```java
MockChatModel mockChatModel = new MockChatModel(List.of(
    new AssistantMessage(stage1Json()),
    new AssistantMessage(stage2Json()),
    new AssistantMessage(validAnalysisJson())
));
ChatClient chatClient = ChatClient.builder(mockChatModel).build();
```

`MockChatModel` returns responses in the order they are provided. Use this approach
to test the happy path and retry scenarios without deep Mockito chaining.

Cover all scenarios from `../common/07_test_scenarios.md § Agent pipeline tests`:

| Scenario | MockChatModel setup |
|---|---|
| Happy path | 3 responses: stage1, stage2, valid analysis JSON |
| Retry on parse failure, then succeeds | 4 responses: stage1, stage2, invalid JSON, valid analysis JSON |
| Exhausts retries | `maxAttempts + 2` responses, all stage1/stage2/invalid |
| Stage 1 upstream failure | Use `MockChatModel` configured to throw; or mock `ChatClient` with Mockito to throw `NonTransientAiException` |

For the retry scenario verify that the second Stage 3 call user-message contains
the validation error text (use `ArgumentCaptor` on a Mockito mock if `MockChatModel`
does not expose captured prompts).

Set `maxAttempts=3` and `backoffDelayMs=0` via `ReflectionTestUtils.setField`.

### 3. `src/test/java/com/example/unlimitagent/web/IncidentControllerTest.java`

`@WebMvcTest(IncidentController.class)` with `IncidentAgentPipeline` mocked via `@MockBean`.

Identical scenarios to `../common/07_test_scenarios.md § REST controller tests`.
No Spring AI involvement — the controller does not depend on Spring AI directly.

---

## Output

Return all three test files labelled with their paths. Include all imports. No explanation.

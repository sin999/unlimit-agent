# Prompt 07 — REST Controller & Global Error Handler

**Common references:** `../common/05_api_contract.md` (endpoint spec, error mapping table)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17.

Available beans:
- `IncidentAgentPipeline.analyze(IncidentRequest)` → `IncidentAnalysis`

Exceptions to handle: `AgentPipelineException`, `ResponseParseException`, `LlmApiException`

---

## Task

### 1. `src/main/java/com/example/unlimitagent/web/IncidentController.java`

`@RestController` with base path `/api/v1`.

#### `POST /api/v1/incidents/analyze`

- Accepts `@RequestBody @Valid IncidentRequest request`
- Returns `pipeline.analyze(request)` with HTTP 200
- No manual serialisation — Jackson handles `IncidentAnalysis` automatically

### 2. `src/main/java/com/example/unlimitagent/web/ErrorResponse.java`

```java
record ErrorResponse(String error, String detail, int status) {}
```

### 3. `src/main/java/com/example/unlimitagent/web/GlobalExceptionHandler.java`

`@RestControllerAdvice` implementing the error mapping from `../common/05_api_contract.md`:

| Exception | HTTP | `error` value |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `"Validation failed"` — `detail` = joined constraint messages |
| `AgentPipelineException` | 502 | `"Agent pipeline error"` |
| `ResponseParseException` | 502 | `"LLM response parse error"` |
| `LlmApiException` | 502 | `"Upstream LLM API error"` |
| `Exception` (catch-all) | 500 | `"Internal server error"` |

Each handler returns `ResponseEntity<ErrorResponse>`.
Log every exception at `ERROR` level with full stack trace before returning the response.

---

## Output

Return all three Java files labelled with their paths. No explanation.

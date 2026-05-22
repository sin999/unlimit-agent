# Prompt 06 — Response Parser & Error Recovery

**Common references:**
- `../common/06_output_schema.json` (JSON Schema for output validation)
- `../common/04_prompt_templates.md` (retry refinement suffix)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17.

The LLM returns a raw `String` that should be valid JSON matching `IncidentAnalysis`.
In practice it may be malformed:
- Wrapped in markdown fences (` ```json ... ``` `)
- Extra or missing fields
- `severity` in wrong case (`"High"` instead of `"high"`)
- Truncated or invalid JSON

**Recovery strategy:**
1. Strip markdown fences.
2. Attempt Jackson deserialisation.
3. On success, run post-deserialisation validation.
4. On failure, validate against JSON Schema and collect error messages.
5. Throw `ResponseParseException` — the pipeline will retry Stage 3 with the error messages appended (retry refinement suffix from `../common/04_prompt_templates.md`).
6. After `agent.retry.max-attempts` failed attempts, wrap in `AgentPipelineException`.

Configuration already in `application.properties`:
```
agent.retry.max-attempts=3
agent.retry.backoff-delay-ms=500
```

---

## Task

### 1. `src/main/java/com/example/unlimitagent/agent/ResponseParser.java`

`@Component` with entry point:

```java
public IncidentAnalysis parse(String rawLlmOutput) throws ResponseParseException
```

**Step A — Strip markdown fences**
Remove ` ```json `, ` ``` `, and surrounding whitespace using a regex or `String.strip()`.

**Step B — Deserialise**
`objectMapper.readValue(cleaned, IncidentAnalysis.class)`.
On success → Step C. On `JsonProcessingException` → Step D.

**Step C — Post-deserialisation validation**
Assert:
- `severity` is not null
- `hypotheses` is not null and has 1–3 items
- Each hypothesis has non-blank `title`, `reasoning`, and 2–3 `nextSteps`

Throw `ResponseParseException` listing all failures if any assertion fails.

**Step D — JSON Schema validation fallback**
Load `../common/06_output_schema.json` from classpath at `schemas/incident_analysis_schema.json`.
Use `com.networknt:json-schema-validator` to validate `cleaned`.
Collect all `ValidationMessage` descriptions and throw `ResponseParseException(errorSummary, rawLlmOutput)`.

### 2. `src/main/resources/schemas/incident_analysis_schema.json`

Copy the JSON Schema verbatim from `../common/06_output_schema.json`.

### 3. `src/main/java/com/example/unlimitagent/agent/ResponseParseException.java`

`RuntimeException` subclass:
- Field: `String rawLlmOutput`
- Constructors:
  - `(String message, String rawLlmOutput)`
  - `(String message, String rawLlmOutput, Throwable cause)`

### 4. Update `IncidentAgentPipeline.java` — add retry loop around Stage 3

Replace the direct `responseParser.parse(stage3Output)` call with:

```
attempt = 1
loop:
  try:
    return responseParser.parse(stage3Output)
  catch ResponseParseException e:
    if attempt >= maxAttempts:
      throw new AgentPipelineException(GENERATE_RESPONSE, e.getMessage(), e)
    refinedUserMessage = stage3UserMessage
      + PromptTemplates.RETRY_SUFFIX_TEMPLATE.replace("<validation_errors>", e.getMessage())
    stage3Output = llmClient.complete(PromptTemplates.STAGE3_SYSTEM, refinedUserMessage)
    attempt++
    Thread.sleep(backoffDelayMs)
    log WARN "Stage GENERATE_RESPONSE parse failed (attempt {}), retrying: {}"
```

Inject `agent.retry.max-attempts` and `agent.retry.backoff-delay-ms` via `@Value`.

---

## Output

Return all files (ResponseParser, schema JSON, ResponseParseException, updated
IncidentAgentPipeline) labelled with their paths. No explanation.

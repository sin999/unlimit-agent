# Prompt 06 — Response Parser & Retry (Spring AI)

**Common references:**
- `../common/06_output_schema.json` (JSON Schema for fallback validation)
- `../common/04_prompt_templates.md` (retry refinement suffix)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 3.4 / Java 17 / Spring AI 1.0.

Because Stage 3 uses `ChatClient.entity(IncidentAnalysis.class)`, Spring AI's
`BeanOutputConverter` handles JSON parsing. `ResponseParser` is therefore simpler than in
the plain Java stack — it only needs to:

1. **Validate** the already-deserialized `IncidentAnalysis` (severity, hypotheses, next_steps).
2. **Provide a raw-string fallback** for cases where `.entity()` fails and the pipeline
   falls back to `.content()` (raw string) on retry.

The retry strategy is the same as `../spring-boot-java/06_response_parser.md`:
on validation failure, retry Stage 3 with the error messages appended to the user message.

---

## Task

### 1. `src/main/java/com/example/unlimitagent/agent/ResponseParser.java`

`@Component` with two entry points:

```java
// Called after entity() succeeds — validates the already-parsed object
public void validateAnalysis(IncidentAnalysis analysis) throws ResponseParseException

// Fallback called on retry when entity() fails — parses raw string
public IncidentAnalysis parseRaw(String rawLlmOutput) throws ResponseParseException
```

**`validateAnalysis`** — same post-deserialisation rules as the plain Java stack:
- `severity` not null
- `hypotheses` not null, 1–3 items
- Each hypothesis: non-blank `title`, `reasoning`, 2–3 `nextSteps`

Collect all failures and throw `ResponseParseException(joinedErrors, null)`.

**`parseRaw`** — mirrors `ResponseParser.parse()` from `../spring-boot-java/`:
1. Strip markdown fences.
2. Jackson `readValue(cleaned, IncidentAnalysis.class)`.
3. On success, call `validateAnalysis(result)`.
4. On `JacksonException`, run JSON Schema validation (classpath `schemas/incident_analysis_schema.json`)
   and throw `ResponseParseException`.

### 2. `src/main/resources/schemas/incident_analysis_schema.json`

Copy verbatim from `../common/06_output_schema.json`.

### 3. `src/main/java/com/example/unlimitagent/agent/ResponseParseException.java`

Identical to `../spring-boot-java/06_response_parser.md`.

### 4. Update `IncidentAgentPipeline.java` — Stage 3 retry loop

Replace the direct `entity()` call with a retry loop:

```
attempt = 1
loop:
  try:
    if attempt == 1:
      result = chatClient.prompt().system(STAGE3_SYSTEM).user(stage3UserMessage)
               .call().entity(IncidentAnalysis.class)
      responseParser.validateAnalysis(result)
      return result
    else:
      // entity() failed on previous attempt; fall back to raw content + manual parse
      rawOutput = chatClient.prompt().system(STAGE3_SYSTEM).user(stage3UserMessage)
                  .call().content()
      return responseParser.parseRaw(rawOutput)
  catch ResponseParseException | Spring AI parse exception → e:
    if attempt >= maxAttempts:
      throw AgentPipelineException(GENERATE_RESPONSE, e.getMessage(), e)
    log WARN "Stage GENERATE_RESPONSE failed (attempt {}), retrying: {}"
    sleep(backoffDelayMs)
    stage3UserMessage = stage3UserMessage
      + RETRY_SUFFIX_TEMPLATE.replace("<validation_errors>", e.getMessage())
    attempt++
```

`maxAttempts` and `backoffDelayMs` injected via `@Value` as in the plain Java stack.

---

## Output

Return all files (ResponseParser, schema JSON, ResponseParseException, updated pipeline)
labelled with their paths. No explanation.

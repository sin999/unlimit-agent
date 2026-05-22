# Prompt 05 — Agent Pipeline (Orchestrator)

**Common references:**
- `../common/01_domain_concepts.md` (domain types)
- `../common/04_prompt_templates.md` (runtime LLM prompts for all three stages)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17.

Available beans:
- `LlmClient` — provider-agnostic interface; `complete(systemPrompt, userMessage)` → `String`
- `KnowledgeBase` — `getSystemDescription()` and `getPastIncidents()`
- `ObjectMapper` — Jackson

Domain types already created: `IncidentRequest`, `IncidentAnalysis`, `Hypothesis`, `Severity`, `AnalysisStage`

The pipeline runs three explicit LLM calls in sequence (not one combined prompt):

| Stage | `AnalysisStage` | What it does |
|---|---|---|
| 1 | `PARSE_INPUT` | Extract structured facts from raw incident text |
| 2 | `ENRICH_WITH_CONTEXT` | Match facts against system knowledge and past incidents |
| 3 | `GENERATE_RESPONSE` | Produce the final `IncidentAnalysis` JSON |

---

## Task

### 1. `src/main/java/com/example/unlimitagent/agent/PromptTemplates.java`

A non-Spring utility class (package-private or public) with static `String` constants
for every system prompt and user prompt template from `../common/04_prompt_templates.md`.

Name the constants clearly, e.g.:
- `STAGE1_SYSTEM`, `STAGE2_SYSTEM`, `STAGE3_SYSTEM`
- `STAGE2_USER_TEMPLATE`, `STAGE3_USER_TEMPLATE`
- `RETRY_SUFFIX_TEMPLATE`

For the user templates with placeholders (`<stage1_output>`, `<system_description>`, etc.),
store the raw template string; the pipeline will do string replacement at runtime using
`String.replace("<placeholder>", value)` or `String.formatted(...)`.

Copy the prompt text verbatim from `../common/04_prompt_templates.md`.

### 2. `src/main/java/com/example/unlimitagent/agent/IncidentAgentPipeline.java`

A `@Service` with:

```java
public IncidentAnalysis analyze(IncidentRequest request)
```

**Stage 1 — PARSE_INPUT**
- `stage1Output = llmClient.complete(PromptTemplates.STAGE1_SYSTEM, request.description())`
- Log `DEBUG "Stage PARSE_INPUT completed: {}"` with output

**Stage 2 — ENRICH_WITH_CONTEXT**
- Build user message by filling the `STAGE2_USER_TEMPLATE` with `stage1Output`,
  `knowledgeBase.getSystemDescription()`, `knowledgeBase.getPastIncidents()`
- `stage2Output = llmClient.complete(PromptTemplates.STAGE2_SYSTEM, userMessage)`
- Log `DEBUG "Stage ENRICH_WITH_CONTEXT completed: {}"` with output

**Stage 3 — GENERATE_RESPONSE**
- Build user message by filling `STAGE3_USER_TEMPLATE` with `stage1Output`, `stage2Output`
- `stage3Output = llmClient.complete(PromptTemplates.STAGE3_SYSTEM, userMessage)`
- Log `DEBUG "Stage GENERATE_RESPONSE completed: {}"` with output

**Parse result**
- Delegate to `ResponseParser.parse(stage3Output)` (implemented in prompt 06)
- Return the resulting `IncidentAnalysis`

**Error handling**
- Wrap each stage call in try/catch; on any exception rethrow as `AgentPipelineException`
  carrying the failing `AnalysisStage` and original cause.

### 3. `src/main/java/com/example/unlimitagent/agent/AgentPipelineException.java`

`RuntimeException` subclass:
- Field: `AnalysisStage failedStage`
- Constructor: `(AnalysisStage stage, String message, Throwable cause)`
- Message format: `"Agent pipeline failed at stage [<stage.getLabel()>]: <message>"`

---

## Output

Return all three files labelled with their paths. No explanation.

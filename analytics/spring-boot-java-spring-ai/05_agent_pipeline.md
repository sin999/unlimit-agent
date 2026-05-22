# Prompt 05 — Agent Pipeline (Spring AI)

**Common references:**
- `../common/01_domain_concepts.md` (domain types)
- `../common/04_prompt_templates.md` (runtime LLM prompts)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 3.4 / Java 17 / Spring AI 1.0.

Available beans:
- `ChatClient` — Spring AI abstraction; handles all providers transparently.
- `KnowledgeBase` — `getSystemDescription()`, `getPastIncidents()`.

The three-stage pipeline is the same as in `../spring-boot-java/05_agent_pipeline.md`.
The only difference is that `ChatClient` replaces the hand-rolled `LlmClient`.

Stage 3 uses `ChatClient.entity(IncidentAnalysis.class)` (Spring AI's
`BeanOutputConverter`) which:
- Generates a JSON format instruction and appends it to the prompt automatically.
- Parses the model response directly into `IncidentAnalysis`.
- Throws `org.springframework.ai.converter.StructuredOutputConverter.OutputParserException`
  on parse failure (wrapped to `ResponseParseException` in the pipeline).

---

## Task

### 1. `src/main/java/com/example/unlimitagent/agent/PromptTemplates.java`

Identical to `../spring-boot-java/05_agent_pipeline.md`.
Copy the prompt text verbatim from `../common/04_prompt_templates.md`.

> **Note:** `STAGE3_SYSTEM` does NOT need to include JSON format instructions — Spring AI
> appends them automatically when `.entity()` is used. Keep the schema description in the
> system prompt as human guidance; Spring AI's format instructions augment it.

### 2. `src/main/java/com/example/unlimitagent/agent/IncidentAgentPipeline.java`

A `@Service` injecting `ChatClient`, `KnowledgeBase`, `ResponseParser`.

```java
public IncidentAnalysis analyze(IncidentRequest request)
```

**Stage 1 — PARSE_INPUT**
```java
String stage1Output = chatClient.prompt()
    .system(PromptTemplates.STAGE1_SYSTEM)
    .user(request.description())
    .call()
    .content();
```
Wrap any Spring AI exception in `AgentPipelineException(PARSE_INPUT, ...)`.

**Stage 2 — ENRICH_WITH_CONTEXT**
```java
String userMessage = PromptTemplates.STAGE2_USER_TEMPLATE
    .replace("<stage1_output>", stage1Output)
    .replace("<system_description>", knowledgeBase.getSystemDescription())
    .replace("<past_incidents>", knowledgeBase.getPastIncidents());

String stage2Output = chatClient.prompt()
    .system(PromptTemplates.STAGE2_SYSTEM)
    .user(userMessage)
    .call()
    .content();
```
Wrap any Spring AI exception in `AgentPipelineException(ENRICH_WITH_CONTEXT, ...)`.

**Stage 3 — GENERATE_RESPONSE (structured output)**
```java
String stage3UserMessage = PromptTemplates.STAGE3_USER_TEMPLATE
    .replace("<stage1_output>", stage1Output)
    .replace("<stage2_output>", stage2Output);

// entity() uses BeanOutputConverter: appends format instructions + parses response
IncidentAnalysis result = chatClient.prompt()
    .system(PromptTemplates.STAGE3_SYSTEM)
    .user(stage3UserMessage)
    .call()
    .entity(IncidentAnalysis.class);
```

Delegate to `ResponseParser.validateAnalysis(result)` for post-deserialisation checks
(severity, hypotheses count, next_steps count). If validation fails, fall through to the
retry loop (see prompt 06).

Wrap Spring AI exceptions in `AgentPipelineException(GENERATE_RESPONSE, ...)`.

### 3. `src/main/java/com/example/unlimitagent/agent/AgentPipelineException.java`

Identical to `../spring-boot-java/05_agent_pipeline.md`.

---

## Output

Return all three files labelled with their paths. No explanation.

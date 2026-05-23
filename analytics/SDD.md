# Software Design Document
## AI Incident Assistant

**Version:** 1.4
**Date:** 2026-05-23
**References:** BRD.md, SRS.md

---

## 1. Design Principles

| Principle | How it is applied |
|---|---|
| Provider agnosticism | The LLM is accessed only through the Spring AI `ChatClient` abstraction. Switching providers requires changing one dependency and one property prefix — no code changes. |
| Fail fast | Missing knowledge files, an unreachable vector store at startup, or an invalid `maxAttempts` value abort startup with a clear message. Silent degradation is not acceptable. |
| Retry over abort | Stage 3 refines its prompt with the validation errors and retries before surfacing a 502. Each retry uses the original user message plus a single error suffix — no accumulation. |
| Minimal state | The service is stateless. ChromaDB is the only persistent store. |
| Single responsibility | Web, pipeline, knowledge, and parsing concerns are fully separated into distinct classes. |

---

## 2. Technology Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21 (LTS) | Long-term support release; full compatibility with Spring Boot 3.x and Spring AI 1.0.0; modern records, sealed classes, and pattern matching available. |
| Web framework | Spring Boot 3.4.5 | Required by Spring AI 1.0.0, which targets Spring Boot 3.x / Spring Framework 6.x. |
| LLM abstraction | Spring AI 1.0.0 `ChatClient` | Provider-agnostic; handles structured output, request advisors, and provider-specific serialisation. |
| Default LLM provider | Anthropic Claude via `spring-ai-starter-model-anthropic` | Swappable — see §2.1. |
| Vector store | ChromaDB via `spring-ai-starter-vector-store-chroma` | Lightweight; runs as a sidecar Docker container with no extra infrastructure. |
| Embedding model | Local ONNX Transformers via `spring-ai-starter-model-transformers` | No additional API key needed. Uses `all-MiniLM-L6-v2` (384 dimensions, ~80 MB). Downloads automatically from HuggingFace on first run. |
| JSON Schema validation | `com.networknt:json-schema-validator:1.5.3` | Produces structured field-level error messages used in the retry prompt. |
| Prompt templates | Thymeleaf 3.1 (`spring-boot-starter-thymeleaf`) in TEXT mode | Structured variable substitution for prompt files using `StringTemplateResolver`; template content is loaded at startup for fail-fast validation and passed directly to the engine at render time. |
| Build tool | Gradle (Groovy DSL) with `org.springframework.boot` and `io.spring.dependency-management` plugins | Groovy DSL (`build.gradle`), not Kotlin DSL (`build.gradle.kts`). Provides BOM import via `dependencyManagement` block; `bootJar` task produces the executable JAR. |
| Observability | Spring Boot Actuator | Provides `/actuator/health` for liveness/readiness probes. |
| API documentation | springdoc-openapi 2.8.8 (`springdoc-openapi-starter-webmvc-ui`) | Auto-generates OpenAPI 3.0 spec and Swagger UI from controller annotations at runtime. UI at `/swagger-ui.html`; JSON spec at `/v3/api-docs`. |

### 2.1 Switching LLM Providers

Replace the starter dependency and update the `spring.ai.*` property prefix. No Java changes required.

| Provider | Starter artifact | Property prefix |
|---|---|---|
| Anthropic (default) | `spring-ai-starter-model-anthropic` | `spring.ai.anthropic.*` |
| OpenAI | `spring-ai-starter-model-openai` | `spring.ai.openai.*` |
| Ollama (local) | `spring-ai-starter-model-ollama` | `spring.ai.ollama.*` |
| Gemini | `spring-ai-starter-model-vertex-ai-gemini` | `spring.ai.vertexai.gemini.*` |

---

## 3. Project Setup

### 3.1 build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.5'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'pt.sin.services'
version = '0.0.2'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.ai:spring-ai-bom:1.0.0"
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
    implementation 'org.springframework.ai:spring-ai-starter-vector-store-chroma'
    implementation 'org.springframework.ai:spring-ai-starter-model-transformers'
    implementation 'com.networknt:json-schema-validator:1.5.3'
    implementation 'org.apache.commons:commons-lang3:3.17.0'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.ai:spring-ai-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.mockito:mockito-core:5.20.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.20.0'
    testImplementation 'net.bytebuddy:byte-buddy:1.17.8'
    testImplementation 'net.bytebuddy:byte-buddy-agent:1.17.8'
}

tasks.named('test') {
    useJUnitPlatform()
    jvmArgs(
        '-XX:+EnableDynamicAgentLoading',
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-opens=java.base/java.util=ALL-UNNAMED'
    )
}
```

Key notes:
- The build script MUST use **Groovy DSL** (`build.gradle`). Do not use Kotlin DSL (`build.gradle.kts`).
- Group `pt.sin.services` and artifact name `unlimit-agent` (matching the parent directory) are the project-standard coordinates.
- Java toolchain is pinned to 21 — the `toolchain` block ensures Gradle uses a JDK 21 regardless of the JDK used to run Gradle itself.
- No Lombok — the domain model uses Java records exclusively.
- Thymeleaf is included for prompt template rendering in TEXT mode. The auto-configured web `TemplateEngine` is not reused — `PromptTemplates` creates its own engine with a `StringTemplateResolver` scoped to prompt content loaded at startup.
- The Spring AI BOM imported via `dependencyManagement` manages all Spring AI artifact versions; do not add explicit versions to Spring AI dependencies.

---

## 4. Configuration Files

### 4.1 src/main/resources/application.properties

Production-safe defaults. DEBUG logging is NOT enabled here.

```properties
spring.application.name=unlimit-agent

spring.ai.anthropic.api-key=${LLM_API_KEY}
spring.ai.anthropic.chat.options.model=${LLM_MODEL:claude-sonnet-4-6}
spring.ai.anthropic.chat.options.max-tokens=2048

spring.ai.vectorstore.chroma.client.host=http://localhost
spring.ai.vectorstore.chroma.client.port=8000
spring.ai.vectorstore.chroma.collection-name=past-incidents
spring.ai.vectorstore.chroma.initialize-schema=true

agent.retry.max-attempts=3
agent.retry.backoff-delay-ms=500
agent.llm.timeout-ms=25000

server.port=8080

spring.mvc.throw-exception-if-no-handler-found=true
spring.web.resources.add-mappings=false

management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never

springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html

logging.level.pt.sin.services.unlimitagent=INFO
```

### 4.2 src/test/resources/application-test.properties

Used by all Spring context tests to satisfy the non-blank API key constraint and prevent real external calls:

```properties
spring.ai.anthropic.api-key=test-key
spring.ai.anthropic.chat.options.model=claude-test-model
spring.ai.anthropic.chat.options.max-tokens=100
```

### 4.3 src/main/resources/application-dev.properties

Local development profile. Activate with `-Dspring.profiles.active=dev` or `SPRING_PROFILES_ACTIVE=dev`.

```properties
logging.level.pt.sin.services.unlimitagent=DEBUG
logging.level.org.springframework.ai=DEBUG
```

**Warning:** enabling this profile logs all LLM prompts and responses including raw incident descriptions. Do not use in production or shared environments.

---

## 5. Package Structure

Base package: `pt.sin.services.unlimitagent`

```
agent/
  IncidentAgentPipeline      Orchestrates the three LLM stages
  PromptTemplates            Loads prompt files from classpath at startup and exposes them
  ResponseParser             Validates and parses LLM output
  AgentPipelineException     Failure thrown by the pipeline, tagged with the failed stage
  ResponseParseException     Thrown when the LLM output cannot be parsed or validated

client/
  LlmApiException            Wraps upstream LLM HTTP errors (status code + raw body)

config/
  SpringAiConfig             Produces the ChatClient bean
  OpenApiConfig              Declares OpenAPI metadata (title, description, version)

knowledge/
  KnowledgeBase              Provides system description, vector-searched past incidents, and runtime ingestion
  PastIncidentSeeder         Seeds ChromaDB from past_incidents.txt at startup

model/
  IncidentRequest            API input
  KnowledgeIngestRequest     Knowledge ingestion API input
  IncidentAnalysis           API output and LLM structured target
  Hypothesis                 Nested inside IncidentAnalysis
  Severity                   Enum: LOW, MEDIUM, HIGH
  AnalysisStage              Enum: EXTRACT_FACTS, ENRICH_CONTEXT, SYNTHESIZE

web/
  IncidentController         POST /api/v1/incidents/analyze
  KnowledgeIngestController  POST /api/v1/admin/incidents/knowledge
  GlobalExceptionHandler     Maps all domain exceptions to HTTP responses
  ErrorResponse              Uniform error response body
```

Prompt template files are stored under `src/main/resources/prompts/` — one `.txt` file per prompt, named after the constant in lower-snake-case. All six files are **Thymeleaf TEXT mode templates**. System prompt files (no substitution needed) contain plain text and are valid Thymeleaf templates with no expressions. User message templates use the unescaped inline expression syntax `[(${variableName})]` for each variable placeholder:

```
src/main/resources/prompts/
  stage1_system.txt
  stage2_system.txt
  stage2_user_template.txt      ← uses [(${stage1Output})], [(${systemDescription})], [(${pastIncidents})]
  stage3_system.txt
  stage3_user_template.txt      ← uses [(${stage1Output})], [(${stage2Output})]
  retry_suffix_template.txt     ← uses [(${validationErrors})]
```

---

## 6. Component Descriptions

### 6.1 SpringAiConfig

A configuration class that produces a single application-scoped `ChatClient` bean. It receives the auto-configured `ChatClient.Builder` (wired by the Anthropic starter from `spring.ai.anthropic.*` properties) and attaches a `SimpleLoggerAdvisor` that logs each prompt and response at DEBUG level. No other class references the provider directly.

Spring AI auto-wires the full chain: the Anthropic starter creates a `ChatModel` bean; the dependency-management infrastructure wraps it in a `ChatClient.Builder`; `SpringAiConfig` builds the final `ChatClient`. The Transformers starter creates the `EmbeddingModel` bean; the Chroma starter uses that plus `spring.ai.vectorstore.chroma.*` properties to create the `VectorStore` bean.

---

### 6.2 OpenApiConfig

A `@Configuration` class that produces a single `OpenAPI` bean. Sets the API title (`"Unlimit Agent API"`), description, and version (matching the project version in `build.gradle`). No controllers or models need additional annotations — springdoc scans them automatically and generates the full spec.

Endpoints exposed at runtime:
- **`/swagger-ui.html`** — Swagger UI (interactive browser)
- **`/v3/api-docs`** — OpenAPI 3.0 JSON spec

---

### 6.3 Domain Model

All domain types are Java records — immutable and directly serialisable by Jackson.

- **IncidentRequest** — single field `description`, annotated `@NotBlank` and `@Size(max = 2000)` so Bean Validation rejects blank or oversized values before the controller method is entered.
- **KnowledgeIngestRequest** — two fields: `incidentId` and `text`, both annotated `@NotBlank`.
- **IncidentAnalysis** — four fields: `category` (String), `summary` (String), `severity` (Severity enum), `hypotheses` (list of Hypothesis). This is both the API response type and the target type for Spring AI's structured output parser.
- **Hypothesis** — three fields: `title`, `reasoning` (both String), and `nextSteps` (list of String). The JSON key for `nextSteps` must be `next_steps` (snake case) — annotate with `@JsonProperty("next_steps")`.
- **Severity** — enum with values `LOW`, `MEDIUM`, `HIGH`. Must parse case-insensitively from JSON — include a static factory method annotated `@JsonCreator` that uppercases the input before calling `valueOf`. Must serialise to lowercase — annotate each value with `@JsonValue` returning the lowercase string.
- **AnalysisStage** — enum with values `EXTRACT_FACTS`, `ENRICH_CONTEXT`, `SYNTHESIZE`. Each value carries a human-readable label string used in error messages: `"Extracting facts"`, `"Enriching context"`, `"Synthesizing analysis"`.

---

### 6.4 Exception Types

- **AgentPipelineException** — runtime exception that wraps any failure inside the pipeline. Carries an `AnalysisStage` field identifying which stage failed. Constructed with the stage, a message, and a cause.
- **ResponseParseException** — runtime exception thrown by `ResponseParser` when the LLM output cannot be deserialised or fails validation. Carries a joined string of all collected error messages.
- **LlmApiException** — runtime exception that wraps upstream LLM API HTTP errors. Carries the HTTP status code and raw response body. The pipeline catches Spring AI provider exceptions and wraps them in this type before re-throwing as `AgentPipelineException`.

---

### 6.5 KnowledgeBase

Constructed with a `ResourceLoader` and a `VectorStore`. At construction time it loads `system_description.txt` from the classpath into a private final field — fail fast with `IllegalStateException` if the file is missing or blank. The loaded content is returned unchanged by `getSystemDescription()`.

`getPastIncidents(String query)` calls `VectorStore.similaritySearch` with the query and `topK=3`, then joins the matching document texts with a double newline separator. If no documents match, it returns the literal string `"No relevant past incidents found."` so the pipeline always has something to substitute into the Stage 2 template.

The query passed to `getPastIncidents` is the **original incident description**, not the Stage 1 JSON output. Natural-language-to-natural-language similarity scores are more accurate for the `all-MiniLM-L6-v2` model than JSON-to-natural-language scores.

`addIncident(String incidentId, String text)` creates a `Document` with the given text and `incidentId` metadata and calls `VectorStore.add()`. This method is called by `KnowledgeIngestController` to support runtime ingestion (FR-4).

---

### 6.6 PastIncidentSeeder

Implements `ApplicationListener<ApplicationReadyEvent>` so it fires after the full Spring context — including the ChromaDB connection — is ready.

On startup it probes the vector store using `VectorStore.count()` (or the equivalent collection count API). If the count is greater than zero, seeding is skipped (idempotent restart). Otherwise it reads `past_incidents.txt` from the classpath, splits the content into blocks using a zero-width lookahead regex on the `[INC-` prefix (so the marker stays at the start of each block), and creates one `Document` per block with an `incidentId` metadata entry. The documents are then added to the vector store in a single call.

If `past_incidents.txt` is not found and the vector store count is zero, `PastIncidentSeeder` MUST throw an `IllegalStateException` with a descriptive message and abort startup.

The parsing method (`parseIncidents`) must be package-private so it can be tested directly without mocking the file system.

---

### 6.7 PromptTemplates

A Spring component (`@Component`) that loads all six Thymeleaf template files from `classpath:prompts/` at construction time using a `ResourceLoader`. Each file's content is read once into a private final `String` field — fail fast with `IllegalStateException` if any file is missing or blank. Storing the content as strings also enables a `StringTemplateResolver`, so the auto-configured web `TemplateEngine` bean is not touched and there is no conflict with HTML view resolution.

Variable substitution uses **Thymeleaf TEXT mode** via a `StringTemplateResolver`. At construction time `PromptTemplates` creates a private `SpringTemplateEngine` (from `org.thymeleaf.spring6`) configured with a `StringTemplateResolver` set to template mode `TEXT`. Each render method passes the pre-loaded template string directly to `templateEngine.process(templateContent, context)` — the resolver treats the string itself as the template source.

> **Important:** use `SpringTemplateEngine`, not the plain `TemplateEngine`. `spring-boot-starter-thymeleaf` brings in `thymeleaf-spring6` but does **not** place OGNL on the classpath. Plain `TemplateEngine` uses `StandardDialect`, which requires OGNL and will throw `ClassNotFoundException: ognl.PropertyAccessor` at runtime. `SpringTemplateEngine` replaces `StandardDialect` with `SpringStandardDialect`, which uses Spring EL — always available in a Spring Boot application.

Template files use Thymeleaf's unescaped inline variable expression syntax — `[(${variableName})]` — which emits the value verbatim without HTML-escaping, preserving newlines and special characters in multi-line prompt values. Variable names are the camelCase equivalents of the SRS `<angle_bracket>` placeholders:

| SRS placeholder | Thymeleaf expression |
|---|---|
| `<stage1_output>` | `[(${stage1Output})]` |
| `<stage2_output>` | `[(${stage2Output})]` |
| `<system_description>` | `[(${systemDescription})]` |
| `<past_incidents>` | `[(${pastIncidents})]` |
| `<validation_errors>` | `[(${validationErrors})]` |

Each render method creates an `org.thymeleaf.context.Context`, sets the relevant variables, and calls `templateEngine.process(templateName, context)` where `templateName` is the file name without extension (e.g. `"stage2_user_template"`).

**Public API:**
- `getStage1System()` / `getStage2System()` / `getStage3System()` — return the pre-loaded system prompt strings directly. These files contain no variable expressions so no rendering step is needed.
- `renderStage2UserMessage(stage1Output, systemDescription, pastIncidents)` — renders `stage2_user_template` with `stage1Output`, `systemDescription`, and `pastIncidents`.
- `renderStage3UserMessage(stage1Output, stage2Output)` — renders `stage3_user_template` with `stage1Output` and `stage2Output`.
- `renderRetrySuffix(validationErrors)` — renders `retry_suffix_template` with `validationErrors`.

---

### 6.8 ResponseParser

A Spring component injected with a Jackson `ObjectMapper`. It has two public methods.

**`validateAnalysis(IncidentAnalysis)`** is called after Stage 3 successfully deserialises a response. It checks all rules in SRS FR-5 (including blank `category` and blank `summary`), collects every violation into a list, then throws a single `ResponseParseException` with the violations joined into one message. It does not throw on the first error — all errors are reported together so the retry prompt is maximally informative.

**`parseRaw(String)`** is called on retry attempts when the structured output path has failed. It strips markdown fences (both ` ```json ` and plain ` ``` ` variants), attempts a Jackson `readValue` into `IncidentAnalysis`, and if successful calls `validateAnalysis` on the result. If Jackson throws, it runs the raw string through JSON Schema validation against `schemas/incident_analysis_schema.json` (loaded from the classpath) to produce structured field-level error messages, then throws `ResponseParseException` with those messages. The schema-derived messages are more useful in the retry prompt than a raw Jackson stack trace.

---

### 6.9 IncidentAgentPipeline

A Spring service injected with `ChatClient`, `KnowledgeBase`, `ResponseParser`, and `PromptTemplates`. Three fields — `maxAttempts`, `backoffDelayMs`, and `llmTimeoutMs` — are injected from `agent.retry.*` and `agent.llm.*` properties via `@Value`. At construction time, validate that `maxAttempts >= 2`; throw `IllegalArgumentException` if not.

The public `analyze(IncidentRequest)` method delegates to three private methods in sequence: `runStage1`, `runStage2`, and `runStage3WithRetry`.

**runStage1** sends the raw description as the user message with `STAGE1_SYSTEM` as the system prompt. Returns the raw LLM string. Wraps any exception in `AgentPipelineException(EXTRACT_FACTS, ...)`.

**runStage2** receives the Stage 1 output and the original description. Builds the user message by calling `prompts.renderStage2UserMessage(stage1Output, knowledgeBase.getSystemDescription(), knowledgeBase.getPastIncidents(originalDescription))`. The original description — not the Stage 1 JSON — is the search query. Returns the raw LLM string. Wraps any exception in `AgentPipelineException(ENRICH_CONTEXT, ...)`.

**runStage3WithRetry** implements the retry loop described in §7.

---

### 6.10 IncidentController

A REST controller with a single `POST /api/v1/incidents/analyze` method. The request body is an `IncidentRequest` annotated with `@Valid` so Bean Validation runs before the method body. The method calls `pipeline.analyze(request)` and wraps the result in a 200 response. No business logic lives here.

---

### 6.11 KnowledgeIngestController

A REST controller with a single `POST /api/v1/admin/incidents/knowledge` method. The request body is a `KnowledgeIngestRequest` annotated with `@Valid`. On success the method calls `knowledgeBase.addIncident(request.incidentId(), request.text())` and returns HTTP 201 with no body.

---

### 6.12 GlobalExceptionHandler

A `@RestControllerAdvice` with one handler method per exception type. Logging levels are calibrated to avoid alert noise from expected client errors:

| Exception type | HTTP status | `error` field | Log level |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `"Validation failed"` | WARN |
| `HttpMediaTypeNotSupportedException` | 415 | `"Unsupported media type"` | WARN |
| `HttpRequestMethodNotSupportedException` | 405 | `"Method not allowed"` | WARN |
| `AgentPipelineException` | 502 | `"Agent pipeline error"` | WARN |
| `ResponseParseException` | 502 | `"LLM response parse error"` | WARN |
| `LlmApiException` | 502 | `"Upstream LLM API error"` | WARN |
| `Exception` (catch-all) | 500 | `"Internal server error"` | ERROR with full stack trace |

Only the catch-all `Exception` handler logs at ERROR. All 4xx and expected 5xx failures log at WARN — these represent operational conditions (LLM misbehaviour, bad client input) rather than programming errors.

---

## 7. Retry Strategy

Stage 3 is the only retried stage because it is the only one that must produce schema-constrained structured output. Stages 1 and 2 produce free-form JSON passed verbatim to the next stage; parse errors there indicate a systemic prompt problem rather than transient model variance.

```
attempt = 1
originalUserMessage = prompts.renderStage3UserMessage(stage1Output, stage2Output)
userMessage = originalUserMessage

loop:
  if attempt == 1:
    call entity(IncidentAnalysis)   ← BeanOutputConverter appends format instructions
    validateAnalysis(result)
    return result on success

  else:
    call content()                  ← plain string; no format instructions injected
    parseRaw(rawOutput)
    return result on success

  on parse or validation failure:
    if attempt >= maxAttempts → throw AgentPipelineException(SYNTHESIZE)
    log WARN with attempt number and errors
    sleep backoffDelayMs
    userMessage = originalUserMessage + prompts.renderRetrySuffix(errors)
                  ↑ rebuilt from original each time — not accumulated
    attempt++

  on Spring AI provider exception:
    wrap in LlmApiException → throw AgentPipelineException(SYNTHESIZE)
```

`entity()` is not retried after the first failure because `BeanOutputConverter` would inject its JSON format instructions a second time, potentially conflicting with the validation-error suffix. Switching to `content()` + `parseRaw()` on retries gives full control over the prompt.

Each retry constructs `userMessage` from `originalUserMessage` plus one fresh error suffix — previous error suffixes are discarded. This prevents unbounded prompt growth and ensures the LLM always sees a clean correction instruction.

---

## 8. Data Flow

### 8.1 Happy Path

```
Client POST /api/v1/incidents/analyze
  └─► IncidentController
      └─► IncidentAgentPipeline.analyze()
          │
          ├─[EXTRACT_FACTS]  LLM call with raw description
          │                  returns: {affected_services, symptoms, error_types, time_context}
          │
          ├─[ENRICH_CONTEXT] ChromaDB similarity search on original description → top-3 incidents
          │                  + system description from cache
          │                  LLM call with enriched context
          │                  returns: {matched_past_incident, likely_category, ...}
          │
          └─[SYNTHESIZE]     LLM call → entity(IncidentAnalysis) → validate → return
      └─► 200 OK with IncidentAnalysis JSON
```

### 8.2 SYNTHESIZE Retry Path

```
SYNTHESIZE, attempt 1: entity() → validation fails
  └─► userMessage = originalUserMessage + suffix(errors1) → attempt 2

SYNTHESIZE, attempt 2: content() → parseRaw() → succeeds → return
  OR
SYNTHESIZE, attempt 2: content() → parseRaw() → fails
  └─► userMessage = originalUserMessage + suffix(errors2) → attempt 3  ← rebuilt, not appended

SYNTHESIZE, attempt 3 (= maxAttempts): still fails
  └─► AgentPipelineException(SYNTHESIZE) → GlobalExceptionHandler → 502
```

### 8.3 Startup Seeding

```
ApplicationReadyEvent
  └─► PastIncidentSeeder
      ├─ count: vectorStore.count()  ← exact count, not similarity probe
      │   ├─ count > 0 → skip, already seeded
      │   └─ count == 0 → parse past_incidents.txt into Documents → vectorStore.add()
      │       └─ past_incidents.txt not found → IllegalStateException → abort startup
      └─► done; application ready to serve requests
```

### 8.4 Runtime Knowledge Ingestion

```
Client POST /api/v1/admin/incidents/knowledge {"incidentId": "INC-105", "text": "..."}
  └─► KnowledgeIngestController
      └─► KnowledgeBase.addIncident("INC-105", "...")
          └─► vectorStore.add([Document(text, {incidentId: "INC-105"})])
      └─► 201 Created
```

---

## 9. Test Strategy

### 9.1 Test Pyramid

| Layer | Class | Approach |
|---|---|---|
| Unit | `ResponseParserTest` | Real `ObjectMapper`; no mocks |
| Unit | `KnowledgeBaseTest` | Mock `ResourceLoader`, `Resource`, and `VectorStore` |
| Unit | `PastIncidentSeederTest` | Mock `VectorStore` and `ResourceLoader`; call `parseIncidents()` directly |
| Unit | `IncidentAgentPipelineTest` | Mock `ChatClient` fluent chain + `KnowledgeBase` |
| MVC slice | `IncidentControllerTest` | `@WebMvcTest`; `IncidentAgentPipeline` as `@MockitoBean` |
| MVC slice | `KnowledgeIngestControllerTest` | `@WebMvcTest`; `KnowledgeBase` as `@MockitoBean` |
| Integration | `UnlimitAgentApplicationTests` | `@SpringBootTest`; `VectorStore` as `@MockitoBean` |

### 9.2 ChatClient Mock Chain

The `ChatClient` fluent call chain — `prompt() → system() → user() → call() → content()/entity()` — must be mocked as five separate mock objects wired together with `when/thenReturn`. Use `lenient()` stubbing for the chain setup in `@BeforeEach` so unused calls in individual tests do not fail. Configure `content()` and `entity()` return values per test.

`getPastIncidents` on the `KnowledgeBase` mock must use `anyString()` as the argument matcher because it takes a query parameter.

**Mock identity for `user()` verification:** in the wired chain below, `.user(msg)` is called on `systemSpec` — not on `userSpec`. `userSpec` is the object *returned by* `systemSpec.user()`. When verifying the user message content (e.g. that the retry suffix was appended), always verify against `systemSpec`:

```java
// Correct — user() is invoked on systemSpec
verify(systemSpec, atLeastOnce()).user(argThat((String msg) -> msg.contains("validation")));

// Wrong — userSpec is what system().user() returns, not who receives the call
verify(userSpec, atLeastOnce()).user(...);  // WantedButNotInvoked
```

`ChatClientRequestSpec.user()` has multiple overloads (`String`, `Resource`, `Consumer<PromptUserSpec>`). When using `argThat`, always provide an explicit type on the lambda parameter — `argThat((String msg) -> ...)` — to resolve the ambiguity at compile time.

### 9.3 @Value Field Injection in Unit Tests

`maxAttempts`, `backoffDelayMs`, and `llmTimeoutMs` are injected by Spring and therefore null in pure unit tests. Set them after constructing the pipeline using `ReflectionTestUtils.setField`. Use `maxAttempts = 3` and `backoffDelayMs = 0` in all pipeline tests.

### 9.4 VectorStore Mock in Integration Tests

`@MockitoBean VectorStore` prevents `ChromaVectorStore` from connecting to a real ChromaDB instance during `@SpringBootTest`. Use `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`) — `@MockBean` (from `org.springframework.boot.test.mock.mockito`) is deprecated as of Spring Boot 3.4 and scheduled for removal. The mock's `count()` method returns `0` by default causing `PastIncidentSeeder` to enter the seeding path and call `vectorStore.add()` — a no-op on the mock. The context loads successfully.

### 9.5 KnowledgeBase Construction in Unit Tests

`KnowledgeBase` reads `system_description.txt` in its constructor. In unit tests, provide a mock `ResourceLoader` that returns a mock `Resource` configured to report `exists() = true` and return a non-blank string from `getContentAsString()`. Construct the `KnowledgeBase` manually in `@BeforeEach`.

**Lenient stubs for partial-failure tests:** the test that verifies startup failure when the file is missing (K-4) overrides `exists()` to return `false`, so `getContentAsString()` is never reached. Mockito strict mode (`MockitoExtension`) treats that unreached stub as an error (`UnnecessaryStubbingException`). Declare the `getContentAsString()` stub with `lenient()` in `@BeforeEach` so it is silently ignored by tests that do not exercise that path:

```java
lenient().when(sysDescResource.getContentAsString(StandardCharsets.UTF_8))
        .thenReturn("System description text.");
```

---

## 10. Deployment

### 10.1 docker-compose.yml

```yaml
version: '3.8'

services:
  chroma:
    image: chromadb/chroma:0.5.23
    ports:
      - "8000:8000"
    volumes:
      - chroma-data:/chroma/chroma
    environment:
      ANONYMIZED_TELEMETRY: "false"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/v1/heartbeat"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      LLM_API_KEY: ${LLM_API_KEY}
      LLM_MODEL: ${LLM_MODEL:-claude-sonnet-4-6}
      SPRING_AI_VECTORSTORE_CHROMA_CLIENT_HOST: http://chroma
      SPRING_AI_VECTORSTORE_CHROMA_CLIENT_PORT: 8000
    deploy:
      resources:
        limits:
          memory: 768M
    depends_on:
      chroma:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 90s

volumes:
  chroma-data:
```

Key notes:
- ChromaDB image is pinned to `0.5.23`. Version 0.6.x introduced an async regression (`cannot unpack non-iterable coroutine object`) that breaks Spring AI's upsert calls. Do not upgrade to 0.6.x until the fix is confirmed. Do not use `latest`.
- The app container has a 768 MB memory limit to accommodate: JVM heap, the ~80 MB ONNX embedding model, and Spring context overhead.
- `start_period: 90s` accounts for the HuggingFace model download on the first run.
- **`chroma-init` is required.** Spring AI 1.0.0's `ChromaVectorStore.afterPropertiesSet()` calls `ChromaApi.getCollection()` during Spring context initialisation, which throws `RuntimeException` (not null) when the collection does not exist — so the `initialize-schema=true` create path is never reached. The `chroma-init` one-shot service pre-creates the collection via `POST /api/v1/collections` with `get_or_create:true` before the app container starts. The `app` service has `chroma-init: condition: service_completed_successfully` in `depends_on`.

### 10.2 Dockerfile

```dockerfile
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q
COPY src/ src/
RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The build stage uses the official `gradle:8-jdk21` image — Gradle 8 and JDK 21 are pre-installed, so no Gradle wrapper files need to be copied. The `dependencies` layer is cached by Docker and only re-runs when `build.gradle` changes. The runtime stage uses the lean `eclipse-temurin:21-jre` image.

### 10.3 Running Locally (without Docker)

```bash
docker compose up chroma -d
LLM_API_KEY=<your-key> ./gradlew bootRun
```

To enable DEBUG logging locally:

```bash
LLM_API_KEY=<your-key> ./gradlew bootRun --args='--spring.profiles.active=dev'
```

The embedding model downloads automatically on first run (~80 MB from HuggingFace). Subsequent startups use the cached model from `~/.cache/huggingface/`.

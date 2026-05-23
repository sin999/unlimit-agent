# Software Design Document
## AI Incident Assistant

**Version:** 1.8
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
| Embedding model | llama.cpp server sidecar via `spring-ai-starter-model-openai` (OpenAI-compatible API) | No additional API key needed. Uses `nomic-embed-text-v1.5` (GGUF Q8_0, 768 dimensions, ~139 MB). Model GGUF file is stored in `./models/` on the host and bind-mounted read-only; zero download at app startup once the file is present. llama.cpp exposes a `/v1/embeddings` endpoint on port 8080 (mapped to host 11434). Spring AI's OpenAI embedding client points to it via `spring.ai.openai.base-url`. |
| JSON Schema validation | `com.networknt:json-schema-validator:1.5.3` | Produces structured field-level error messages used in the retry prompt. |
| Prompt templates | Thymeleaf 3.1 (`spring-boot-starter-thymeleaf`) in TEXT mode | Structured variable substitution for prompt files using `StringTemplateResolver`; template content is loaded at startup for fail-fast validation and passed directly to the engine at render time. |
| Build tool | Gradle (Groovy DSL) with `org.springframework.boot` and `io.spring.dependency-management` plugins | Groovy DSL (`build.gradle`), not Kotlin DSL (`build.gradle.kts`). Multi-module project (`api` + `impl`); BOM import in root `subprojects` block; `bootJar` task in `impl`. |
| API code generation | `org.openapi.generator` plugin v7.12.0 (`spring` generator) | Generates Spring delegate interfaces and model POJOs from `openapi.yaml`; `api` module contains only the spec and build config — no hand-written Java. |
| Access control | Spring Security 6 + `spring-boot-starter-oauth2-resource-server` | Stateless JWT Bearer token validation; role-based endpoint authorization; toggled by the `security.enabled` property. |
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

The project is a Gradle multi-module build. `settings.gradle` declares two submodules:

```groovy
rootProject.name = 'unlimit-agent'
include 'api', 'impl'
```

### 3.1 build.gradle (root — coordination only)

The root build file applies no `java` plugin itself. All common configuration lives in the `subprojects` block:

```groovy
plugins {
    id 'org.springframework.boot' version '3.4.5' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    group = 'pt.sin.services'
    version = '0.0.3'

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
            mavenBom "org.springframework.boot:spring-boot-dependencies:3.4.5"
            mavenBom "org.springframework.ai:spring-ai-bom:1.0.0"
        }
    }
}
```

### 3.2 api/build.gradle — OpenAPI code generation

The `api` module contains **no hand-written Java**. Its only source artefact is `api/src/main/openapi/openapi.yaml`. The build file wires the `org.openapi.generator` plugin to compile the spec into a JAR of Spring delegate interfaces and model POJOs:

```groovy
plugins {
    id 'org.openapi.generator' version '7.12.0'
}

openApiGenerate {
    generatorName = 'spring'
    inputSpec     = "$projectDir/src/main/openapi/openapi.yaml"
    outputDir     = "$buildDir/generated"
    apiPackage    = 'pt.sin.services.unlimitagent.api'
    modelPackage  = 'pt.sin.services.unlimitagent.model'
    configOptions = [
        interfaceOnly          : 'true',
        useSpringBoot3         : 'true',
        useTags                : 'true',
        openApiNullable        : 'false',
        hideGenerationTimestamp: 'true',
    ]
}

sourceSets.main.java.srcDir "$buildDir/generated/src/main/java"
compileJava.dependsOn tasks.openApiGenerate

dependencies {
    compileOnly 'org.springframework:spring-web'
    compileOnly 'org.springframework:spring-context'
    compileOnly 'jakarta.validation:jakarta.validation-api'
    compileOnly 'jakarta.annotation:jakarta.annotation-api'
    compileOnly 'jakarta.servlet:jakarta.servlet-api'
    compileOnly 'io.swagger.core.v3:swagger-annotations-jakarta:2.2.29'
    compileOnly 'io.swagger.core.v3:swagger-core-jakarta:2.2.29'
    implementation 'com.fasterxml.jackson.core:jackson-annotations'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

Key configuration options:
- `interfaceOnly=true` — generates delegate interfaces, not controllers. `impl` controllers implement these interfaces.
- `useTags=true` — names interfaces after the OpenAPI `tags` value (`Incidents` → `IncidentsApi`).
- `useSpringBoot3=true` — emits `jakarta.*` imports, not `javax.*`.
- `openApiNullable=false` — suppresses the Jackson-nullable wrapper library dependency.
- All five `compileOnly` Jakarta/Swagger dependencies are required. `jakarta.annotation` is needed for `@Generated`; `jakarta.servlet` is needed for the generated `ApiUtil` helper class. Both are compile-time only — the `impl` module provides them transitively at runtime.

**What is generated** into `api/build/generated/src/main/java/`:

| Generated artefact | Package | Description |
|---|---|---|
| `IncidentsApi` | `pt.sin.services.unlimitagent.api` | Spring delegate interface for `POST /api/v1/incidents/analyze`; carries all `@Tag`, `@Operation`, `@ApiResponse`, `@RequestMapping` annotations |
| `KnowledgeBaseAdminApi` | `pt.sin.services.unlimitagent.api` | Spring delegate interface for `POST /api/v1/admin/incidents/knowledge` |
| `ApiUtil` | `pt.sin.services.unlimitagent.api` | Generated helper; not used directly |
| `IncidentRequest`, `IncidentAnalysis`, `Hypothesis`, `Severity`, `KnowledgeIngestRequest`, `ErrorResponse` | `pt.sin.services.unlimitagent.model` | POJO models with fluent setters and standard getters |

### 3.3 impl/build.gradle — application module

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation project(':api')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'
    implementation 'org.springframework.ai:spring-ai-starter-vector-store-chroma'
    implementation 'org.springframework.ai:spring-ai-starter-model-openai'
    implementation 'com.networknt:json-schema-validator:1.5.3'
    implementation 'org.apache.commons:commons-lang3:3.17.0'
    implementation 'org.apache.commons:commons-collections4:4.5.0'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.ai:spring-ai-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.mockito:mockito-core:5.20.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.20.0'
    testImplementation 'net.bytebuddy:byte-buddy:1.17.8'
    testImplementation 'net.bytebuddy:byte-buddy-agent:1.17.8'
    testImplementation 'org.springframework.security:spring-security-test'
}

configurations.all {
    resolutionStrategy.force 'io.swagger.core.v3:swagger-annotations:2.2.30'
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
- Build scripts MUST use **Groovy DSL** (`build.gradle`). Do not use Kotlin DSL (`build.gradle.kts`).
- `implementation project(':api')` brings the generated interfaces and models onto the `impl` classpath; `impl` has no `model/` package of its own.
- No Lombok — the domain model is generated from the OpenAPI spec.
- Thymeleaf is included for prompt template rendering in TEXT mode. The auto-configured web `TemplateEngine` is not reused — `PromptTemplates` creates its own engine with a `StringTemplateResolver` scoped to prompt content loaded at startup.
- The Spring AI BOM in the root `subprojects` block manages all Spring AI artifact versions; do not add explicit versions to Spring AI dependencies.
- `spring-ai-starter-model-anthropic` (chat) and `spring-ai-starter-model-openai` (embeddings via llama.cpp) coexist. Use `spring.ai.model.chat=anthropic` and `spring.ai.model.embedding=openai` in `application.properties` to disambiguate — without these two properties Spring Boot will fail to start with `NoUniqueBeanDefinitionException` (two `ChatModel` beans found).
- `resolutionStrategy.force 'io.swagger.core.v3:swagger-annotations:2.2.30'` is required to resolve a version conflict between the OpenAPI generator output and springdoc-openapi.

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

spring.ai.openai.base-url=${LLAMA_CPP_BASE_URL:http://localhost:11434}
spring.ai.openai.api-key=${LLAMA_CPP_API_KEY:none}
spring.ai.openai.embedding.options.model=nomic-embed-text
spring.ai.model.chat=anthropic
spring.ai.model.embedding=openai

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

# Security — disabled by default; set SECURITY_ENABLED=true in production
security.enabled=${SECURITY_ENABLED:false}
spring.security.oauth2.resourceserver.jwt.issuer-uri=${JWT_ISSUER_URI:}
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${JWT_JWK_SET_URI:}
security.jwt.roles-claim=roles
```

### 4.2 src/test/resources/application-test.properties

Used by all Spring context tests to satisfy the non-blank API key constraint and prevent real external calls:

```properties
spring.ai.anthropic.api-key=test-key
spring.ai.anthropic.chat.options.model=claude-test-model
spring.ai.anthropic.chat.options.max-tokens=100
spring.ai.openai.base-url=http://localhost:11434
spring.ai.openai.api-key=none
spring.ai.openai.embedding.options.model=nomic-embed-text
spring.ai.model.chat=anthropic
spring.ai.model.embedding=openai
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

The project is split across two Gradle modules. The `api` module contains only the spec; the `impl` module contains all application code.

### api module

```
api/
  src/main/openapi/
    openapi.yaml                           ← only hand-authored source in this module

  build/generated/src/main/java/
    pt.sin.services.unlimitagent.api/
      IncidentsApi                         Generated — POST /api/v1/incidents/analyze delegate interface
      KnowledgeBaseAdminApi                Generated — POST /api/v1/admin/incidents/knowledge delegate interface
      ApiUtil                              Generated helper

    pt.sin.services.unlimitagent.model/
      IncidentRequest                      Generated POJO — API input
      KnowledgeIngestRequest               Generated POJO — knowledge ingestion API input
      IncidentAnalysis                     Generated POJO — API output and LLM structured target
      Hypothesis                           Generated POJO — nested inside IncidentAnalysis
      Severity                             Generated enum — values: low, medium, high (lowercase, spec-exact)
      ErrorResponse                        Generated POJO — uniform error response body
```

### impl module

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
  ChromaConfig               Produces the custom ChromaApi bean (HTTP/1.1 + null-return fix)
  ChromaProperties           @ConfigurationProperties for spring.ai.vectorstore.chroma.*
  OpenApiConfig              Declares OpenAPI info block (title, description, version) for Swagger UI
  SecurityConfig             Produces the SecurityFilterChain bean; branches on security.enabled

repositories/
  IncidentRepository         Interface — declares getSystemDescription(), findSimilarIncidents(query), save(id, text)
  IncidentVectorRepository   @Repository — implements IncidentRepository using VectorStore
  PastIncidentSeeder         Seeds ChromaDB from past_incidents.txt at startup

model/
  AnalysisStage              Enum: EXTRACT_FACTS, ENRICH_CONTEXT, SYNTHESIZE  ← hand-written; not in spec

controllers/
  IncidentController         Implements IncidentsApi — POST /api/v1/incidents/analyze
  KnowledgeIngestController  Implements KnowledgeBaseAdminApi — POST /api/v1/admin/incidents/knowledge
  GlobalExceptionHandler     Maps all domain exceptions to HTTP responses
```

Note: `IncidentRequest`, `IncidentAnalysis`, `Hypothesis`, `Severity`, `KnowledgeIngestRequest`, and `ErrorResponse` live in the `:api` module (`model` package) — not in `impl`. The `impl` module references them as a project dependency.

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

Spring AI auto-wires the full chain: `spring.ai.model.chat=anthropic` selects `AnthropicChatModel` as the `ChatModel` bean; `spring.ai.model.embedding=openai` selects `OpenAiEmbeddingModel` as the `EmbeddingModel` bean (pointing to the llama.cpp sidecar). Both properties are **required** — omitting them causes `NoUniqueBeanDefinitionException` at startup because both the Anthropic and OpenAI starters register their own `ChatModel`. The Chroma starter uses the `OpenAiEmbeddingModel` plus `spring.ai.vectorstore.chroma.*` properties to create the `VectorStore` bean.

---

### 6.1a ChromaConfig

A `@Configuration` class that produces a custom `ChromaApi` bean. Two specific issues require this override instead of relying on Spring AI's default auto-configuration:

1. **HTTP/1.1 enforcement** — ChromaDB's uvicorn server rejects HTTP/2 upgrade requests with a `400`. The default `RestClient` may attempt HTTP/2. The fix is to build a `JdkClientHttpRequestFactory` backed by an `HttpClient` pinned to `HTTP_1_1` and pass it to the `RestClient.Builder`.

2. **`getCollection()` null-return bug** — Spring AI 1.0.0's `ChromaVectorStore.afterPropertiesSet()` calls `ChromaApi.getCollection()` to check whether the collection exists before creating it (`initialize-schema=true`). The default implementation throws a `RuntimeException` when the collection does not exist instead of returning `null`, so the create path is never reached. The fix is to override `getCollection()` with a `try/catch` that returns `null` on exception.

`ChromaConfig` is annotated `@EnableConfigurationProperties(ChromaProperties.class)`. `ChromaProperties` is a `@ConfigurationProperties(prefix = "spring.ai.vectorstore.chroma")` record with a nested `Client` record (`host`, `port`) and a top-level `collectionName` field. The `ChromaApi` constructor receives the full `host:port` string.

---

### 6.2 OpenApiConfig

A `@Configuration` class that produces a single `OpenAPI` bean. Sets the API title (`"Unlimit Agent API"`), description, and version (matching the project version in `build.gradle`). No controllers or models need additional annotations — springdoc scans them automatically and generates the full spec.

Endpoints exposed at runtime:
- **`/swagger-ui.html`** — Swagger UI (interactive browser)
- **`/v3/api-docs`** — OpenAPI 3.0 JSON spec

---

### 6.3 SecurityConfig

A `@Configuration @EnableWebSecurity` class. Reads `${security.enabled:false}` via `@Value`. Produces a single `SecurityFilterChain` bean whose behaviour branches on that flag.

**Disabled path (default — `security.enabled=false`):**
- CSRF disabled
- All requests `permitAll()` — no authentication required
- At bean creation, logs `WARN: "Security is DISABLED — all endpoints are accessible without authentication"`

**Enabled path (`security.enabled=true`):**
- CSRF disabled (stateless REST API)
- Session management: `STATELESS` (no server-side sessions)
- Authorization rules (evaluated top-to-bottom):
  - `GET /actuator/health`, `GET /swagger-ui/**`, `GET /swagger-ui.html`, `GET /v3/api-docs/**` → `permitAll()`
  - `POST /api/v1/incidents/analyze` → `hasAnyRole("USER", "ADMIN")`
  - `POST /api/v1/admin/incidents/knowledge` → `hasRole("ADMIN")`
  - all other requests → `authenticated()`
- `oauth2ResourceServer(jwt(...))` with a custom `JwtAuthenticationConverter`
- Custom `AuthenticationEntryPoint` → writes a 401 `ErrorResponse` JSON body directly to `HttpServletResponse`
- Custom `AccessDeniedHandler` → writes a 403 `ErrorResponse` JSON body directly to `HttpServletResponse`

**JwtAuthenticationConverter:**

A private helper method builds the converter:
- `JwtGrantedAuthoritiesConverter` with `authoritiesClaimName` set from `${security.jwt.roles-claim:roles}`
- `authorityPrefix = "ROLE_"` (Spring Security convention)
- Wrapped in `JwtAuthenticationConverter`

**JWT claim contract:**

```json
{
  "sub": "service-account-id",
  "roles": ["user"],
  "iss": "https://your-idp.example.com",
  "exp": 9999999999
}
```

| `roles` claim value | Mapped Spring authority | Can call analyze | Can call ingest |
|---|---|---|---|
| `"user"` | `ROLE_USER` | Yes | No (403) |
| `"admin"` | `ROLE_ADMIN` | Yes | Yes |

**Why 401/403 are NOT handled by GlobalExceptionHandler:**
Spring Security intercepts the request before it reaches the `DispatcherServlet`. `@RestControllerAdvice` only handles exceptions thrown from within the servlet dispatch — it never sees `AuthenticationException` or `AccessDeniedException`. The custom entry point and access denied handler wired inside `SecurityConfig` are the correct extension points and emit `ErrorResponse` JSON directly onto the `HttpServletResponse`.

**JWKS / Issuer URI fail-fast:**
If `security.enabled=true` but both `JWT_ISSUER_URI` and `JWT_JWK_SET_URI` are blank, Spring Boot's OAuth2 resource server auto-configuration throws at startup — satisfying NFR-3.4 without any extra code.

---

### 6.4 Domain Model

All API-facing domain types are **generated POJOs** produced by the `org.openapi.generator` plugin from `openapi.yaml`. They are not Java records. Each generated class has:
- Standard getters (e.g. `getCategory()`, `getSeverity()`)
- Fluent setters that return `this` (e.g. `new IncidentAnalysis().category("x").severity(Severity.HIGH)`)
- Jackson `@JsonProperty` annotations for snake_case field names

**Generated model classes** (in the `:api` module, `pt.sin.services.unlimitagent.model` package):

- **IncidentRequest** — single field `description`. The spec marks it `required: true` and `minLength: 1`, `maxLength: 2000` — the generator emits `@NotNull` and `@Size(min=1, max=2000)`. Because `description` is required, the generator produces a required-args constructor: `new IncidentRequest("text")` is valid.
- **KnowledgeIngestRequest** — two fields: `incidentId` and `text`, both required with `minLength: 1` in the spec — the generator emits `@NotNull @Size(min=1)` on each.
- **IncidentAnalysis** — four fields: `category` (String), `summary` (String), `severity` (Severity enum), `hypotheses` (list of Hypothesis). This is both the API response type and the target type for Spring AI's structured output parser. Construct with fluent setters: `new IncidentAnalysis().category("x").summary("y").severity(Severity.HIGH).hypotheses(list)`.
- **Hypothesis** — three fields: `title`, `reasoning` (both String), and `nextSteps` (list of String). The JSON key for `nextSteps` is `next_steps` (snake case) — the generator emits `@JsonProperty("next_steps")` automatically.
- **Severity** — generated enum with values `LOW`, `MEDIUM`, `HIGH`. The generator produces a `fromValue(String)` factory method that performs **exact, case-sensitive** matching against the spec values (`"low"`, `"medium"`, `"high"`). Unlike the earlier hand-written version, there is no case-folding: `"HIGH"` or `"CRITICAL"` will throw `IllegalArgumentException`. JSON deserialisation uses the same `fromValue` — pass lowercase strings in LLM output.
- **ErrorResponse** — three fields: `error` (String), `detail` (String), `status` (Integer). Construct with fluent setters: `new ErrorResponse().error("msg").detail("details").status(400)`.

**Hand-written model class** (in `impl`, `pt.sin.services.unlimitagent.model` package):

- **AnalysisStage** — enum with values `EXTRACT_FACTS`, `ENRICH_CONTEXT`, `SYNTHESIZE`. Each value carries a human-readable label string used in error messages: `"Extracting facts"`, `"Enriching context"`, `"Synthesizing analysis"`. Not part of the API contract — lives in `impl`.

---

### 6.5 Exception Types

- **AgentPipelineException** — runtime exception that wraps any failure inside the pipeline. Carries an `AnalysisStage` field identifying which stage failed. Constructed with the stage, a message, and a cause.
- **ResponseParseException** — runtime exception thrown by `ResponseParser` when the LLM output cannot be deserialised or fails validation. Carries a joined string of all collected error messages.
- **LlmApiException** — runtime exception that wraps upstream LLM API HTTP errors. Carries the HTTP status code and raw response body. The pipeline catches Spring AI provider exceptions and wraps them in this type before re-throwing as `AgentPipelineException`.

---

### 6.6 IncidentRepository / IncidentVectorRepository

`IncidentRepository` is a plain Java interface in the `repositories` package. It declares three methods and one constant:
- `String METADATA_INCIDENT_ID = "incidentId"` — metadata key used when storing documents
- `String getSystemDescription()` — returns the cached system description text
- `String findSimilarIncidents(String query)` — searches the vector store and returns formatted results
- `void save(String incidentId, String text)` — adds a new incident document to the vector store

`IncidentVectorRepository` is a `@Repository` class that implements `IncidentRepository`. Constructed with a `ResourceLoader` and a `VectorStore`. At construction time it loads `system_description.txt` from `classpath:knowledge/system_description.txt` into a private final field — fail fast with `IllegalStateException` if the file is missing or blank. The loaded content is returned unchanged by `getSystemDescription()`.

`findSimilarIncidents(String query)` calls `VectorStore.similaritySearch` with the query and `topK=3`, then joins the matching document texts with a double newline separator. If no documents match, it returns the literal string `"No relevant past incidents found."` so the pipeline always has something to substitute into the Stage 2 template.

The query passed to `findSimilarIncidents` is the **original incident description**, not the Stage 1 JSON output. Natural-language-to-natural-language similarity scores are more accurate for the `all-MiniLM-L6-v2` model than JSON-to-natural-language scores.

`save(String incidentId, String text)` creates a `Document` with the given text and `incidentId` metadata and calls `VectorStore.add()`. This method is called by `KnowledgeIngestController` to support runtime ingestion (FR-4).

---

### 6.7 PastIncidentSeeder

Implements `ApplicationListener<ApplicationReadyEvent>` so it fires after the full Spring context — including the ChromaDB connection — is ready.

On startup it probes the vector store by calling `VectorStore.similaritySearch` with the query `"incident"`, `topK=1`, and `similarityThreshold=0.0`. If any document is returned, seeding is skipped (idempotent restart). **Do not use `VectorStore.count()` — Spring AI 1.0.0's `ChromaVectorStore` does not implement that method.** Otherwise it reads `past_incidents.txt` from the classpath, splits the content into blocks using a zero-width lookahead regex on the `[INC-` prefix (so the marker stays at the start of each block), and creates one `Document` per block with an `incidentId` metadata entry extracted via `Pattern.compile("\\[(INC-\\d+)\\]")`. The documents are then added to the vector store in a single call.

If `past_incidents.txt` is not found and the collection is empty, `PastIncidentSeeder` MUST throw an `IllegalStateException` with a descriptive message and abort startup.

The `collectionHasDocuments()` probe method must be package-private (not private) so `PastIncidentSeederTest` can spy on it. The parsing method (`parseIncidents`) must also be package-private for direct testing.

---

### 6.8 PromptTemplates

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

### 6.9 ResponseParser

A Spring component injected with a Jackson `ObjectMapper`. It has two public methods.

**`validateAnalysis(IncidentAnalysis)`** is called after Stage 3 successfully deserialises a response. It checks all rules in SRS FR-5 (including blank `category` and blank `summary`), collects every violation into a list, then throws a single `ResponseParseException` with the violations joined into one message. It does not throw on the first error — all errors are reported together so the retry prompt is maximally informative.

**`parseRaw(String)`** is called on retry attempts when the structured output path has failed. It strips markdown fences (both ` ```json ` and plain ` ``` ` variants), attempts a Jackson `readValue` into `IncidentAnalysis`, and if successful calls `validateAnalysis` on the result. If Jackson throws, it runs the raw string through JSON Schema validation against `schemas/incident_analysis_schema.json` (loaded from the classpath) to produce structured field-level error messages, then throws `ResponseParseException` with those messages. The schema-derived messages are more useful in the retry prompt than a raw Jackson stack trace.

---

### 6.10 IncidentAgentPipeline

A Spring service injected with `ChatClient`, `IncidentRepository`, `ResponseParser`, and `PromptTemplates`. Three fields — `maxAttempts`, `backoffDelayMs`, and `llmTimeoutMs` — are injected from `agent.retry.*` and `agent.llm.*` properties via `@Value`. At construction time, validate that `maxAttempts >= 2`; throw `IllegalArgumentException` if not.

The public `analyze(IncidentRequest)` method delegates to three private methods in sequence: `runStage1`, `runStage2`, and `runStage3WithRetry`.

**runStage1** sends the raw description as the user message with `STAGE1_SYSTEM` as the system prompt. Returns the raw LLM string. Wraps any exception in `AgentPipelineException(EXTRACT_FACTS, ...)`.

**runStage2** receives the Stage 1 output and the original description. Builds the user message by calling `prompts.renderStage2UserMessage(stage1Output, knowledgeBase.getSystemDescription(), knowledgeBase.findSimilarIncidents(originalDescription))`. The original description — not the Stage 1 JSON — is the search query. Returns the raw LLM string. Wraps any exception in `AgentPipelineException(ENRICH_CONTEXT, ...)`.

**runStage3WithRetry** implements the retry loop described in §7.

---

### 6.11 IncidentController

A `@RestController` that **implements the generated `IncidentsApi` interface**. All routing (`@RequestMapping`), validation (`@Valid`), and OpenAPI annotations (`@Tag`, `@Operation`, `@ApiResponse`) are declared on the generated interface — the controller class carries only `@RestController` and the `@Override` method body.

```java
@RestController
public class IncidentController implements IncidentsApi {
    private final IncidentAgentPipeline pipeline;
    public IncidentController(IncidentAgentPipeline pipeline) { this.pipeline = pipeline; }

    @Override
    public ResponseEntity<IncidentAnalysis> analyze(IncidentRequest incidentRequest) {
        return ResponseEntity.ok(pipeline.analyze(incidentRequest));
    }
}
```

No business logic lives here. Bean Validation on `IncidentRequest` runs via the `@Valid` annotation on the generated interface method.

---

### 6.12 KnowledgeIngestController

A `@RestController` that **implements the generated `KnowledgeBaseAdminApi` interface**. Same pattern as `IncidentController` — all routing and annotations come from the generated interface.

```java
@RestController
public class KnowledgeIngestController implements KnowledgeBaseAdminApi {
    private final IncidentRepository knowledgeBase;
    public KnowledgeIngestController(IncidentRepository knowledgeBase) { this.knowledgeBase = knowledgeBase; }

    @Override
    public ResponseEntity<Void> ingest(KnowledgeIngestRequest knowledgeIngestRequest) {
        knowledgeBase.save(knowledgeIngestRequest.getIncidentId(), knowledgeIngestRequest.getText());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
```

Note the return type is `ResponseEntity<Void>` (not `void` + `@ResponseStatus`) — this is the signature the generated interface declares. Accessor calls use getters (`getIncidentId()`, `getText()`) because the model is a generated POJO, not a record. The controller injects `IncidentRepository` (the interface), not `IncidentVectorRepository` (the concrete class).

---

### 6.13 GlobalExceptionHandler

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

**Spring Security exceptions (401/403) are not handled here.** `AuthenticationException` and `AccessDeniedException` are intercepted by Spring Security before the request reaches the `DispatcherServlet`, so `@RestControllerAdvice` never sees them. They are handled by the custom `AuthenticationEntryPoint` (→ 401) and `AccessDeniedHandler` (→ 403) wired inside `SecurityConfig` (§6.3), which write the standard `ErrorResponse` JSON directly to `HttpServletResponse`.

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
      ├─ probe: similaritySearch("incident", topK=1, threshold=0.0)
      │   ├─ non-empty → skip, already seeded
      │   └─ empty → parse past_incidents.txt into Documents → vectorStore.add()
      │       └─ past_incidents.txt not found → IllegalStateException → abort startup
      └─► done; application ready to serve requests
```

Note: `VectorStore.count()` is not used — Spring AI 1.0.0's `ChromaVectorStore` does not implement it. The probe uses `similaritySearch` with `similarityThreshold(0.0)` to accept any stored document regardless of similarity score.

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
| Unit | `IncidentVectorRepositoryTest` | Mock `ResourceLoader`, `Resource`, and `VectorStore` |
| Unit | `PastIncidentSeederTest` | Mock `VectorStore`; call `parseIncidents()` and `collectionHasDocuments()` directly |
| Unit | `IncidentAgentPipelineTest` | Mock `ChatClient` fluent chain + `IncidentRepository` |
| MVC slice | `IncidentControllerTest` | `@WebMvcTest(IncidentController.class)`; `IncidentAgentPipeline` as `@MockitoBean` — mock the concrete class, no service interface |
| MVC slice | `KnowledgeIngestControllerTest` | `@WebMvcTest(KnowledgeIngestController.class)`; `IncidentRepository` as `@MockitoBean` |
| Security slice | `SecurityConfigTest` | `@WebMvcTest` with `@Import(SecurityConfig.class)`; `jwt()` post-processor from `spring-security-test`; both pipeline and knowledge base mocked |
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

### 9.4a Model Construction in Tests (Generated POJOs)

Because the domain models are generated POJOs (not records), test code must use fluent setters rather than record constructors or multi-arg constructors:

```java
// IncidentRequest — use required-args constructor (description is required in spec)
new IncidentRequest("payments failing")

// IncidentAnalysis — fluent setters
new IncidentAnalysis()
    .category("External payment provider issue")
    .summary("PayGate is not responding.")
    .severity(Severity.HIGH)
    .hypotheses(List.of(...))

// Hypothesis — fluent setters
new Hypothesis()
    .title("PayGate degradation")
    .reasoning("Timeouts only on PayGate.")
    .nextSteps(List.of("Check status page", "Review logs"))

// ErrorResponse — fluent setters
new ErrorResponse().error("msg").detail("details").status(400)
```

All accessor calls in test assertions use getters: `result.getCategory()`, `result.getSeverity()`, `h.getTitle()`, `h.getNextSteps()`, etc.

### 9.4 VectorStore Mock in Integration Tests

`@MockitoBean VectorStore` prevents `ChromaVectorStore` from connecting to a real ChromaDB instance during `@SpringBootTest`. Use `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`) — `@MockBean` (from `org.springframework.boot.test.mock.mockito`) is deprecated as of Spring Boot 3.4 and scheduled for removal. The mock's `similaritySearch()` returns an empty list by default, causing `PastIncidentSeeder.collectionHasDocuments()` to return `false` and enter the seeding path — `vectorStore.add()` is a no-op on the mock. The context loads successfully.

### 9.5 IncidentVectorRepository Construction in Unit Tests

`IncidentVectorRepository` reads `system_description.txt` in its constructor. In unit tests, provide a mock `ResourceLoader` that returns a mock `Resource` configured to report `exists() = true` and return a non-blank string from `getContentAsString()`. Construct the repository manually in `@BeforeEach`.

**Lenient stubs for partial-failure tests:** the test that verifies startup failure when the file is missing (K-4) overrides `exists()` to return `false`, so `getContentAsString()` is never reached. Mockito strict mode (`MockitoExtension`) treats that unreached stub as an error (`UnnecessaryStubbingException`). Declare the `getContentAsString()` stub with `lenient()` in `@BeforeEach` so it is silently ignored by tests that do not exercise that path:

```java
lenient().when(sysDescResource.getContentAsString(StandardCharsets.UTF_8))
        .thenReturn("System description text.");
```

### 9.6 Security Slice Test Pattern

`SecurityConfigTest` uses `@WebMvcTest` with `@Import(SecurityConfig.class)` and `@TestPropertySource` to toggle `security.enabled`. Use `SecurityMockMvcRequestPostProcessors.jwt()` from `spring-security-test` — **not** `@WithMockUser`, which is form-based and bypasses the JWT resource server path:

```java
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// User role — analyze succeeds
mockMvc.perform(post("/api/v1/incidents/analyze")
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"description\":\"payments failing\"}"))
        .andExpect(status().isOk());

// Admin role — ingest succeeds
mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"incidentId\":\"INC-106\",\"text\":\"Some text\"}"))
        .andExpect(status().isCreated());

// No token — 401
mockMvc.perform(post("/api/v1/incidents/analyze")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"description\":\"payments failing\"}"))
        .andExpect(status().isUnauthorized());

// User token on admin endpoint — 403
mockMvc.perform(post("/api/v1/admin/incidents/knowledge")
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"incidentId\":\"INC-106\",\"text\":\"Some text\"}"))
        .andExpect(status().isForbidden());
```

To activate `security.enabled=true` for a test class or method:
```java
@TestPropertySource(properties = "security.enabled=true")
```

**Existing controller tests are unaffected.** Standard `@WebMvcTest` slices do not load `SecurityConfig` (it is not a controller, so it falls outside the default slice). Existing tests in `IncidentControllerTest` and `KnowledgeIngestControllerTest` continue to run with all endpoints open.

---

## 10. Deployment

### 10.1 docker-compose.yml

```yaml
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

  chroma-init:
    image: alpine/curl:8.11.1
    entrypoint: ["/bin/sh", "/chroma-init.sh"]
    volumes:
      - ./scripts/chroma-init.sh:/chroma-init.sh:ro
    depends_on:
      chroma:
        condition: service_healthy

  llama-cpp-init:
    image: alpine/curl:8.11.1
    entrypoint: ["/bin/sh", "-c"]
    command:
      - |
        if [ -f /models/nomic-embed-text.gguf ]; then
          echo 'Model already present, skipping download'
        else
          echo 'Downloading nomic-embed-text (~139 MB)...'
          curl -fL -o /models/nomic-embed-text.gguf.tmp \
            https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q8_0.gguf \
            && mv /models/nomic-embed-text.gguf.tmp /models/nomic-embed-text.gguf \
            && echo 'Model ready'
        fi
    volumes:
      - llama-cpp-models:/models

  llama-cpp:
    image: ghcr.io/ggml-org/llama.cpp:server-b9294
    command:
      - "--model"
      - "/models/nomic-embed-text.gguf"
      - "--host"
      - "0.0.0.0"
      - "--port"
      - "8080"
      - "--embedding"
      - "--pooling"
      - "mean"
    ports:
      - "11434:8080"
    volumes:
      - llama-cpp-models:/models
    depends_on:
      llama-cpp-init:
        condition: service_completed_successfully
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 15s

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      LLM_API_KEY: ${LLM_API_KEY}
      LLM_MODEL: ${LLM_MODEL:-claude-sonnet-4-6}
      SPRING_AI_VECTORSTORE_CHROMA_CLIENT_HOST: http://chroma
      SPRING_AI_VECTORSTORE_CHROMA_CLIENT_PORT: 8000
      LLAMA_CPP_BASE_URL: http://llama-cpp:8080
      LLAMA_CPP_API_KEY: none
      SECURITY_ENABLED: ${SECURITY_ENABLED:-false}
      JWT_ISSUER_URI: ${JWT_ISSUER_URI:-}
      JWT_JWK_SET_URI: ${JWT_JWK_SET_URI:-}
    depends_on:
      chroma:
        condition: service_healthy
      chroma-init:
        condition: service_completed_successfully
      llama-cpp:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s

volumes:
  chroma-data:
```

Key notes:
- ChromaDB image is pinned to `0.5.23`. Version 0.6.x introduced an async regression that breaks Spring AI's upsert calls. Do not upgrade to 0.6.x until the fix is confirmed. Do not use `latest`.
- **`chroma-init` is required.** Spring AI 1.0.0's `ChromaVectorStore.afterPropertiesSet()` calls `ChromaApi.getCollection()` during context initialisation, which throws `RuntimeException` (not null) when the collection does not exist — so the `initialize-schema=true` create path is never reached. `chroma-init` pre-creates the ChromaDB tenant and database via the REST API. The `ChromaConfig` override (§6.1a) handles the null-return fix so `initialize-schema=true` creates the collection itself.
- **`llama-cpp-init` downloads the model automatically on first run.** It downloads `nomic-embed-text-v1.5.Q8_0.gguf` (~139 MB) into the `llama-cpp-models` Docker volume. The download is atomic — curl writes to a `.tmp` file first and only renames it on success, so an interrupted download never leaves a corrupted model file. On subsequent `docker compose up` calls the file already exists and the init container exits immediately. `llama-cpp` depends on `llama-cpp-init: service_completed_successfully` and does not start until the model is ready.
- `LLAMA_CPP_BASE_URL: http://llama-cpp:8080` overrides the default `http://localhost:11434` so the app container reaches the llama.cpp sidecar by its Docker network name. The internal llama.cpp port is 8080; it is mapped to host port 11434 for local testing.
- `--embedding --pooling mean` — `--embedding` enables the `/v1/embeddings` OpenAI-compatible endpoint; `--pooling mean` is required for nomic-embed-text-v1.5 (uses mean pooling, not CLS token pooling).
- `start_period: 60s` — the app no longer downloads any model at runtime, so startup is ~5–10 seconds.

### 10.2 Dockerfile

```dockerfile
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY api/build.gradle api/
COPY impl/build.gradle impl/
RUN gradle dependencies --no-daemon -q
COPY api/src/ api/src/
COPY impl/src/ impl/src/
RUN gradle :impl:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/impl/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The build stage uses the official `gradle:8-jdk21` image — Gradle 8 and JDK 21 are pre-installed, so no Gradle wrapper files need to be copied. The `dependencies` layer is cached by Docker and only re-runs when build files change. Each module's `build.gradle` is copied first (before source) so the dependency cache layer is not invalidated by source changes. The `openApiGenerate` task runs automatically as part of `:impl:bootJar` because `impl` depends on `:api` and `:api:compileJava` depends on `openApiGenerate`. The runtime stage copies from `impl/build/libs/` — the `bootJar` task writes there because `org.springframework.boot` is applied to `impl` only.

### 10.3 Docker Compose Startup

#### Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin) installed and running
- `LLM_API_KEY` — Anthropic API key
- `./models/nomic-embed-text.gguf` present (one-time download, see below)

#### Step 1 — Start the full stack

```bash
LLM_API_KEY=<your-key> docker compose up --build
```

Expected startup sequence and approximate times (first run):

| Step | Service | Time |
|---|---|---|
| Build app image (Gradle + Docker layers) | `app` (build stage) | ~2 min (cached on subsequent runs) |
| ChromaDB healthy | `chroma` | ~5 s |
| ChromaDB tenant + database created | `chroma-init` | ~2 s |
| Embedding model downloaded to volume | `llama-cpp-init` | ~2 min first run; instant on repeat |
| llama.cpp model loaded, `/health` responds | `llama-cpp` | ~5 s |
| Spring Boot context + seeding | `app` | ~10 s |

All subsequent `docker compose up` calls skip the build cache and model download — total time is under 30 seconds.

Run detached (background) using `nohup` to prevent the shell session killing containers:

```bash
LLM_API_KEY=<your-key> nohup docker compose up --build > /tmp/compose.log 2>&1 &
```

#### Step 3 — Verify all services are healthy

```bash
docker compose ps
```

Expected output (all services `healthy` or `exited 0`):

```
NAME                             IMAGE                                     STATUS
unlimit-agent-chroma-1           chromadb/chroma:0.5.23                    Up (healthy)
unlimit-agent-chroma-init-1      alpine/curl:8.11.1                        Exited (0)
unlimit-agent-llama-cpp-init-1   alpine/curl:8.11.1                        Exited (0)
unlimit-agent-llama-cpp-1        ghcr.io/ggml-org/llama.cpp:server-b9294   Up (healthy)
unlimit-agent-app-1              unlimit-agent-app                         Up (healthy)
```

Verify the app health endpoint:

```bash
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

Verify the embedding service:

```bash
curl -s http://localhost:11434/health
# Expected: {"status":"ok"}
```

Open the Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

#### Step 4 — Stop the stack

```bash
docker compose down
```

This stops and removes containers but **preserves** the `chroma-data` volume (your vector store data). To wipe everything including stored incidents:

```bash
docker compose down -v
```

The `./models/nomic-embed-text.gguf` file is never removed by compose — it is on the host filesystem.

#### Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `LLM_API_KEY` | Yes | — | Anthropic API key |
| `LLM_MODEL` | No | `claude-sonnet-4-6` | Anthropic model ID |
| `SECURITY_ENABLED` | No | `false` | Enable JWT auth |
| `JWT_ISSUER_URI` | If security enabled | — | OIDC issuer URI |
| `JWT_JWK_SET_URI` | If security enabled | — | JWKS endpoint URI |

#### Running app locally against Docker sidecars

To run the Spring Boot app from source (e.g., for debugging) while keeping sidecars in Docker:

```bash
# Start only the sidecars
docker compose up chroma chroma-init llama-cpp-init llama-cpp -d

# Run the app with Gradle
LLM_API_KEY=<your-key> ./gradlew :impl:bootRun

# Optional: enable DEBUG logging
LLM_API_KEY=<your-key> ./gradlew :impl:bootRun --args='--spring.profiles.active=dev'
```

#### Troubleshooting

**`container name already in use` on `docker compose up`**

Leftover containers from a previous failed run. Clean up and retry:

```bash
docker compose down --remove-orphans
docker rm -f $(docker ps -aq --filter name=unlimit-agent) 2>/dev/null
docker compose up --build
```

**`llama-cpp` exits with code 1 — `model is corrupted or incomplete`**

The `llama-cpp-init` download was interrupted before the atomic rename completed, leaving a stale `.tmp` file. Remove the volume and let the init container re-download:

```bash
docker compose down -v
docker compose up --build
```

**`app` fails to start — `NoUniqueBeanDefinitionException`**

Both `spring.ai.model.chat=anthropic` and `spring.ai.model.embedding=openai` must be set in `application.properties` and `application-test.properties`. These properties disambiguate the Anthropic and OpenAI starters that each register a `ChatModel` bean.

**ChromaDB `400` errors on first startup**

`chroma-init` may not have completed before the app started. Re-run:

```bash
docker compose restart app
```

If it persists, the ChromaDB tenant/database was not created. Bring the stack down with `-v` and start fresh.

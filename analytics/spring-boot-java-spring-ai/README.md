# Stack — Spring Boot / Java / Spring AI

**Language:** Java 17+  
**Framework:** Spring Boot 3.4.x *(see compatibility note below)*  
**Build tool:** Gradle (Groovy DSL)  
**LLM abstraction:** Spring AI `ChatClient`  
**Structured output:** Spring AI `BeanOutputConverter<IncidentAnalysis>`  
**Tests:** JUnit 5 + Mockito + `@WebMvcTest` + Spring AI test utilities  
**Package root:** `com.example.unlimitagent`

## Key differences from `../spring-boot-java/`

| Concern | `spring-boot-java` | `spring-boot-java-spring-ai` |
|---|---|---|
| LLM abstraction | Hand-rolled `LlmClient` interface | Spring AI `ChatClient` |
| Provider config | `LlmProperties` + `RestClientConfig` | Spring AI auto-configuration |
| Provider switching | Change `@Primary` implementation | Change starter dependency + properties |
| Stage 3 parsing | Custom `ResponseParser` + JSON Schema | `ChatClient.entity(IncidentAnalysis.class)` |
| Retry | Manual loop in pipeline | Spring AI `RetryAdvisor` + manual refined-prompt retry |
| Boilerplate | ~5 infrastructure classes | ~1 config class |

## Compatibility note

Spring AI 1.0.x targets **Spring Boot 3.4.x** (Spring Framework 6.x).  
The sibling stack uses Spring Boot 4.x; if that version is required, check whether
a Spring AI release compatible with Spring Boot 4 / Spring Framework 7 is available
before applying these prompts. If not, set `org.springframework.boot` to `3.4.x`
in `build.gradle` for this stack.

## Prompt sequence

| # | File | Produces |
|---|------|----------|
| 01 | `01_project_setup.md` | `build.gradle`, `application.properties` |
| 02 | `02_domain_models.md` | *(unchanged from `../spring-boot-java/`)* |
| 03 | `03_knowledge_base.md` | *(unchanged from `../spring-boot-java/`)* |
| 04 | `04_spring_ai_config.md` | `SpringAiConfig.java`, `LlmApiException.java` |
| 05 | `05_agent_pipeline.md` | `PromptTemplates`, `IncidentAgentPipeline`, `AgentPipelineException` |
| 06 | `06_response_parser.md` | `ResponseParser`, `ResponseParseException`, updated pipeline with retry |
| 07 | `07_rest_api.md` | *(unchanged from `../spring-boot-java/`)* |
| 08 | `08_tests.md` | Unit + slice tests using Spring AI test support |

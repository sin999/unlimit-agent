# Stack — Spring Boot / Java

**Language:** Java 17  
**Framework:** Spring Boot 4.0.6  
**Build tool:** Gradle (Groovy DSL)  
**HTTP client:** Spring `RestClient` (Spring 6, no extra SDK)  
**JSON:** Jackson (via `spring-boot-starter-web`)  
**Validation:** `spring-boot-starter-validation`  
**Retry:** `spring-retry` + `spring-boot-starter-aop`  
**Schema validation:** `com.networknt:json-schema-validator`  
**Boilerplate reduction:** Lombok (optional)  
**Tests:** JUnit 5 + Mockito + `@WebMvcTest` / `MockMvc`  
**Package root:** `com.example.unlimitagent`

## Prompt sequence

Feed these prompts to an LLM in order.
Each prompt lists which `../common/` files to prepend for full context.

| # | File | Produces |
|---|------|----------|
| 01 | `01_project_setup.md` | `build.gradle`, `application.properties` |
| 02 | `02_domain_models.md` | Java records/enums under `model/` |
| 03 | `03_knowledge_base.md` | `KnowledgeBase.java` Spring component |
| 04 | `04_llm_client.md` | `LlmClient`, `LlmApiException`, `LlmProperties`, `RestClientConfig`, `AnthropicLlmClient` |
| 05 | `05_agent_pipeline.md` | `PromptTemplates`, `IncidentAgentPipeline`, `AgentPipelineException` |
| 06 | `06_response_parser.md` | `ResponseParser`, `ResponseParseException`, updated pipeline with retry |
| 07 | `07_rest_api.md` | `IncidentController`, `ErrorResponse`, `GlobalExceptionHandler` |
| 08 | `08_tests.md` | Unit + slice tests |

**Note:** Prompts 05 and 06 both touch `IncidentAgentPipeline` — apply prompt 06's
changes as an update/diff to the file produced in prompt 05.

## Knowledge-base text files

The verbatim content for `system_description.txt` and `past_incidents.txt` is in
`../common/02_knowledge_content.md`. Generate those files first (prompt 03) or
copy them directly from the common spec.

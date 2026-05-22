# Common — Stack-Agnostic Specifications

This folder contains everything that is independent of programming language or framework.
Stack-specific prompt folders reference these files instead of repeating the content.

| File | Contains |
|---|---|
| `01_domain_concepts.md` | Domain entity definitions, field constraints, JSON output format |
| `02_knowledge_content.md` | Verbatim content of the two knowledge-base text files and loading rules |
| `03_llm_client_contract.md` | `LlmClient` interface spec, `LlmApiException` shape, config keys, provider-agnostic design |
| `04_prompt_templates.md` | All three stage runtime LLM prompts and the retry refinement suffix |
| `05_api_contract.md` | REST endpoint spec, request/response shapes, error mapping table |
| `06_output_schema.json` | JSON Schema (draft-07) for `IncidentAnalysis` — used for output validation |
| `07_test_scenarios.md` | Test cases for parser, pipeline, and HTTP layer in plain English |

## How stack prompts use these files

Each stack-specific prompt lists its **Common references** at the top.
When feeding a stack prompt to an LLM standalone, prepend the referenced common files
so the LLM has full context.

## Adding a new stack

Create a new subfolder (e.g. `fastapi-python/`, `express-ts/`) following the same
numbered sequence as `spring-boot-java/`. Reference common files instead of duplicating
content. Only describe what is genuinely specific to that stack.

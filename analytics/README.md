# Prompts — AI Incident Assistant

Code-generation prompts for the `unlimit-agent` service.

## Structure

```
prompts/
  common/               # Stack-agnostic: domain, contracts, prompt texts, schema, test scenarios
  spring-boot-java/     # Spring Boot 4 / Java 17 / Gradle implementation
```

The `common/` folder contains everything that is independent of language or framework.
Stack folders contain only what is specific to that stack and reference `../common/` for shared content.

## Available stacks

| Folder | Language | Framework |
|---|---|---|
| `spring-boot-java/` | Java 17 | Spring Boot 4, Gradle |

To add a new stack (e.g. `fastapi-python/`, `express-ts/`), create a new subfolder following
the same numbered-prompt sequence and reference `../common/` instead of duplicating content.
See `common/README.md` for guidance.

## How to use

1. Open the stack subfolder you want to use (e.g. `spring-boot-java/`).
2. Read its `README.md` for the prompt sequence.
3. For each stack prompt, prepend the **Common references** files listed at the top of that prompt
   before feeding it to an LLM — this gives the LLM full context without repeating it every time.
4. Apply generated files to the project, then proceed to the next prompt.

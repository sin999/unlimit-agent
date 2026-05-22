# Prompt 01 — Project Setup & Dependencies

**Common references:** `../common/03_llm_client_contract.md` (for required config keys)

---

## Context

You are working on a Spring Boot 4.0.6 / Java 17 project called `unlimit-agent`.
The current `build.gradle` is minimal:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
description = 'unlimit-agent'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

The current `application.properties` only has:

```
spring.application.name=unlimit-agent
```

## Task

Update `build.gradle` and `application.properties` so the project is ready to
build the HTTP REST service.

### `build.gradle` — add these dependencies

- `spring-boot-starter-web` — REST endpoints
- `spring-boot-starter-validation` — `@Valid` / `@NotBlank` on request DTOs
- `com.fasterxml.jackson.core:jackson-databind` — JSON serialisation (explicit, even though transitive from web)
- `org.springframework.retry:spring-retry` + `org.springframework.boot:spring-boot-starter-aop` — `@Retryable` support
- `com.networknt:json-schema-validator:1.5.3` — JSON Schema validation for LLM output
- `org.projectlombok:lombok` (optional + annotationProcessor scope) — reduce boilerplate

Do NOT add Spring AI or any other LLM framework.
The LLM API is called directly via Spring's `RestClient` (available since Spring 6).

### `application.properties` — add these keys

```
# LLM provider — set all three via environment variables (values are provider-specific)
llm.api.key=${LLM_API_KEY}
llm.api.base-url=${LLM_API_BASE_URL}
llm.api.model=${LLM_API_MODEL}
llm.api.max-tokens=2048
# Optional: provider-specific version header (e.g. Anthropic requires anthropic-version)
llm.api.provider-version=${LLM_API_PROVIDER_VERSION:}

# Agent retry
agent.retry.max-attempts=3
agent.retry.backoff-delay-ms=500

# Server
server.port=8080

# Logging
logging.level.com.example.unlimitagent=DEBUG
```

## Output

Return the complete updated contents of both files, each labelled with its path.
No explanation.

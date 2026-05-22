# Prompt 01 — Project Setup & Dependencies (Spring AI)

**Common references:** `../common/03_llm_client_contract.md` (config keys)

---

## Context

Spring Boot 3.4.x / Java 17 project. Spring AI replaces the hand-rolled HTTP client.

## Task

### `build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.5'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
    // Web
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Spring AI — swap the starter to change providers (openai, ollama, gemini, etc.)
    implementation 'org.springframework.ai:spring-ai-anthropic-spring-boot-starter'

    // JSON Schema validation for LLM output fallback
    implementation 'com.networknt:json-schema-validator:1.5.3'

    // Boilerplate reduction
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.ai:spring-ai-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**To switch providers** change only the starter:
- OpenAI: `spring-ai-openai-spring-boot-starter`
- Ollama: `spring-ai-ollama-spring-boot-starter`
- Gemini: `spring-ai-vertex-ai-gemini-spring-boot-starter`

### `application.properties`

```properties
spring.application.name=unlimit-agent

# Anthropic provider (swap prefix to match your chosen starter)
# spring.ai.anthropic.* | spring.ai.openai.* | spring.ai.ollama.*
spring.ai.anthropic.api-key=${LLM_API_KEY}
spring.ai.anthropic.chat.options.model=${LLM_MODEL:claude-sonnet-4-6}
spring.ai.anthropic.chat.options.max-tokens=2048

# Agent retry
agent.retry.max-attempts=3
agent.retry.backoff-delay-ms=500

# Server
server.port=8080

# Logging
logging.level.com.example.unlimitagent=DEBUG
logging.level.org.springframework.ai=DEBUG
```

## Output

Return the complete contents of both files labelled with their paths. No explanation.

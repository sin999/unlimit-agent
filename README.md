# unlimit-agent

**Version:** 0.0.3

AI Incident Assistant — a Spring Boot application that accepts a free-text incident description and returns a structured triage analysis: category, severity, and ranked diagnostic hypotheses with concrete next steps.

## Architecture

Three-stage LLM pipeline backed by a vector knowledge base of past incidents:

1. **EXTRACT_FACTS** — extracts affected services, symptoms, and error types from the incident description
2. **ENRICH_CONTEXT** — retrieves semantically similar past incidents from ChromaDB and matches context
3. **SYNTHESIZE** — produces structured `IncidentAnalysis` (category, severity, hypotheses) with retry and validation

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.4.5 |
| LLM provider | Anthropic Claude (via Spring AI 1.0.0) |
| Vector store | ChromaDB 0.5.23 |
| Embeddings | llama.cpp server · `nomic-embed-text-v1.5` GGUF (OpenAI-compatible API) |
| JSON validation | `com.networknt:json-schema-validator:1.5.3` |
| API docs | springdoc-openapi 2.8.8 (Swagger UI) |
| Java | 21 |
| Build | Gradle (Groovy DSL) |

## Prerequisites

- Docker and Docker Compose
- Anthropic API key

## Running

### Start the stack

```bash
LLM_API_KEY=sk-ant-... docker compose up --build
```

On first run the embedding model (`nomic-embed-text-v1.5`, ~139 MB) is downloaded automatically into a Docker volume by the `llama-cpp-init` container. Subsequent starts skip the download and are ready in under 30 seconds.

### Explore the API

Once the stack is healthy, open the interactive API explorer:

**➜ http://localhost:8080/swagger-ui.html**

Or verify the health endpoint:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Stopping

```bash
docker compose down        # keep ChromaDB data
docker compose down -v     # also delete ChromaDB volume
```

### Without Docker (app only)

Requires ChromaDB on `localhost:8000` and llama.cpp on `localhost:11434`:

```bash
docker compose up chroma chroma-init llama-cpp-init llama-cpp -d
LLM_API_KEY=sk-ant-... ./gradlew :impl:bootRun
```

## API

### Analyze an Incident

```
POST /api/v1/incidents/analyze
Content-Type: application/json

{"description": "Card payments are failing with timeout errors."}
```

Response:
```json
{
  "category": "External payment provider issue",
  "summary": "PayGate is not responding.",
  "severity": "high",
  "hypotheses": [
    {
      "title": "PayGate degradation",
      "reasoning": "Timeouts on PayGate calls.",
      "next_steps": ["Check PayGate status page", "Review payment-service error logs"]
    }
  ]
}
```

### Ingest Knowledge (Admin)

```
POST /api/v1/admin/incidents/knowledge
Content-Type: application/json

{"incidentId": "INC-105", "text": "Full incident description and resolution..."}
```

Returns `201 Created`.

### Health Check

```
GET /actuator/health
```

## API Documentation

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI JSON spec |

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `LLM_API_KEY` | — | Anthropic API key (required) |
| `LLM_MODEL` | `claude-sonnet-4-6` | Anthropic model ID |
| `SECURITY_ENABLED` | `false` | Enable JWT auth |
| `JWT_ISSUER_URI` | — | OIDC issuer URI (if security enabled) |
| `JWT_JWK_SET_URI` | — | JWKS endpoint URI (if security enabled) |

## Tests

```bash
./gradlew test
```

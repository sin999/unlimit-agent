# unlimit-agent

**Version:** 0.0.2

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
| Vector store | ChromaDB 0.5.23 (`spring-ai-starter-vector-store-chroma`) |
| Embeddings | ONNX `all-MiniLM-L6-v2` (local, auto-downloads) |
| JSON validation | `com.networknt:json-schema-validator:1.5.3` |
| API docs | springdoc-openapi 2.8.8 (Swagger UI) |
| Utilities | Apache Commons Lang3 3.17.0 |
| Java | 21 |
| Build | Gradle (Groovy DSL) |

## Prerequisites

- Docker and Docker Compose
- Anthropic API key

## Running Locally

```bash
LLM_API_KEY=sk-ant-... docker compose up --build
```

### With Docker Compose (recommended)

Runs the full stack (app + ChromaDB) in containers:

```bash
export LLM_API_KEY=sk-ant-...
docker compose up --build
```

The app is ready when the health check passes (~2 minutes on first run due to ONNX model download). Check status with:

```bash
docker compose ps
```

### Without Docker (app only)

Requires ChromaDB already running on `localhost:8000`:

```bash
export LLM_API_KEY=sk-ant-...
./gradlew bootRun
```

For debug logging (includes raw incident descriptions — non-production only):

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Stopping

```bash
docker compose down          # keep ChromaDB data
docker compose down -v       # also delete ChromaDB volume
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
  "severity": "HIGH",
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

Returns `201 Created`. Adds or updates the incident in the vector store without redeployment.

### Health Check

```
GET /actuator/health
```

## API Documentation (Swagger UI)

Interactive API explorer available at:

```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON spec:

```
http://localhost:8080/v3/api-docs
```

## Configuration

| Property | Env var | Default |
|---|---|---|
| `spring.ai.anthropic.api-key` | `LLM_API_KEY` | — |
| `spring.ai.anthropic.chat.options.model` | `LLM_MODEL` | `claude-sonnet-4-6` |
| `spring.ai.vectorstore.chroma.client.host` | `SPRING_AI_VECTORSTORE_CHROMA_CLIENT_HOST` | `http://localhost` |
| `spring.ai.vectorstore.chroma.client.port` | `SPRING_AI_VECTORSTORE_CHROMA_CLIENT_PORT` | `8000` |
| `agent.retry.max-attempts` | — | `3` |
| `agent.retry.backoff-delay-ms` | — | `500` |
| `agent.llm.timeout-ms` | — | `25000` |

## Tests

```bash
./gradlew test
```

28 tests across 7 test classes:

| Class | Tests | Coverage |
|---|---|---|
| `ResponseParserTest` | P-1–P-8 | JSON parsing, schema validation, blank field checks |
| `IncidentAgentPipelineTest` | A-1–A-6 | Pipeline stages, retry logic, error propagation |
| `KnowledgeBaseTest` | K-1–K-4 | Vector search, fallback, system description loading |
| `PastIncidentSeederTest` | S-1–S-3 | Incident parsing, idempotent seeding |
| `IncidentControllerTest` | C-1–C-6 | Analyze endpoint, validation, error mappings |
| `KnowledgeIngestControllerTest` | I-1–I-3 | Knowledge ingestion endpoint |
| `UnlimitAgentApplicationTests` | X-1 | Context loads |

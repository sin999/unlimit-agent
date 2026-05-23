# Software Requirements Specification
## AI Incident Assistant

**Version:** 1.2  
**Date:** 2026-05-22  
**Status:** Approved  
**References:** BRD.md

---

## 1. Introduction

### 1.1 Purpose

This document specifies the functional and non-functional requirements for the AI Incident Assistant. It is implementation-stack-agnostic: it defines *what* the system must do, not *how* it is built. All technology choices, framework details, and implementation patterns are in SDD.md.

### 1.2 Scope

The AI Incident Assistant is a stateless HTTP service with a primary analysis endpoint and a secondary knowledge ingestion endpoint. It accepts a free-text incident description, runs it through a three-stage LLM pipeline enriched by a semantic knowledge base of past incidents, and returns a structured triage analysis. It also exposes a runtime endpoint for adding new incidents to the knowledge base without redeployment.

### 1.3 Definitions

| Term | Definition |
|---|---|
| **LLM** | Large Language Model — external text-generation service called via API |
| **Agent pipeline** | Multi-stage orchestration in which each stage sends a prompt to the LLM and passes its output as context to the next stage |
| **Vector store** | A database that stores text as numeric embeddings and supports semantic similarity search |
| **Embedding** | A fixed-length numeric vector that represents the semantic meaning of a piece of text |
| **Structured output** | LLM response that the service parses directly into a typed object rather than treating as free text |
| **Retry refinement** | Re-sending a failed Stage 3 prompt with the validation error messages appended, so the LLM can self-correct |

---

## 2. System Overview

The system consists of two HTTP endpoints, an internal three-stage pipeline, and a knowledge layer:

```
Client
  │  POST /api/v1/incidents/analyze           {"description": "..."}
  │  POST /api/v1/admin/incidents/knowledge   {"incidentId": "...", "text": "..."}
  │  GET  /actuator/health
  ▼
[ Web Layer ]
  │
  ▼
[ Agent Pipeline ]                            (analysis endpoint only)
  Stage 1 EXTRACT_FACTS:   extract structured facts from the raw description
  Stage 2 ENRICH_CONTEXT:  retrieve similar past incidents and enrich with architecture knowledge
  Stage 3 SYNTHESIZE:      generate the final structured IncidentAnalysis
                           (with retry on parse/validation failure)
  │
  ├── [ LLM API ]        (external; called in each stage)
  └── [ Knowledge Layer ]
        ├── system_description.txt  (static; loaded at startup)
        └── Vector Store            (past incidents; seeded at startup; updatable at runtime)
```

---

## 3. Functional Requirements

### FR-1 — HTTP API

| ID | Requirement |
|---|---|
| FR-1.1 | The service MUST expose `POST /api/v1/incidents/analyze` |
| FR-1.2 | The request body MUST be JSON with a required non-blank string field `description` |
| FR-1.3 | A blank or missing `description` MUST return HTTP 400 with an error body (see §6) |
| FR-1.4 | A successful analysis MUST return HTTP 200 with an `IncidentAnalysis` JSON body (see §5) |
| FR-1.5 | An unrecoverable pipeline or parse failure MUST return HTTP 502 |
| FR-1.6 | Any other unexpected error MUST return HTTP 500 |
| FR-1.7 | All error responses MUST use the error body format defined in §6 |
| FR-1.8 | A request without `Content-Type: application/json` MUST return HTTP 415 |
| FR-1.9 | A request to an unsupported HTTP method on any defined path MUST return HTTP 405 |
| FR-1.10 | A request body exceeding 16 KB MUST return HTTP 400 |

### FR-2 — Three-Stage Agent Pipeline

The pipeline MUST execute three stages sequentially. Each stage calls the LLM with an independent prompt. Stage outputs are passed as context to subsequent stages.

#### FR-2.1 — Stage 1: EXTRACT_FACTS

| ID | Requirement |
|---|---|
| FR-2.1.1 | The user message MUST be the raw incident description |
| FR-2.1.2 | The system prompt MUST be `STAGE1_SYSTEM` (see §7) |
| FR-2.1.3 | The output is a raw JSON string passed verbatim to Stage 2 |
| FR-2.1.4 | Any LLM API error MUST be reported as a pipeline failure at stage `EXTRACT_FACTS` |

#### FR-2.2 — Stage 2: ENRICH_CONTEXT

| ID | Requirement |
|---|---|
| FR-2.2.1 | The system prompt MUST be `STAGE2_SYSTEM` (see §7) |
| FR-2.2.2 | The user message MUST be built from `STAGE2_USER_TEMPLATE` (see §7) by substituting: Stage 1 output, system architecture description, and retrieved past incidents |
| FR-2.2.3 | Past incidents MUST be retrieved from the vector store using the **original incident description** as the semantic search query; the top 3 results MUST be used |
| FR-2.2.4 | The system architecture description MUST be loaded from `system_description.txt` (see §8.1) |
| FR-2.2.5 | The output is a raw JSON string passed verbatim to Stage 3 |
| FR-2.2.6 | Any LLM API error MUST be reported as a pipeline failure at stage `ENRICH_CONTEXT` |

#### FR-2.3 — Stage 3: SYNTHESIZE

| ID | Requirement |
|---|---|
| FR-2.3.1 | The system prompt MUST be `STAGE3_SYSTEM` (see §7) |
| FR-2.3.2 | The user message MUST be built from `STAGE3_USER_TEMPLATE` (see §7) by substituting Stage 1 and Stage 2 outputs |
| FR-2.3.3 | The LLM response MUST be parsed into an `IncidentAnalysis` object |
| FR-2.3.4 | The parsed object MUST be validated according to FR-5 |
| FR-2.3.5 | On parse or validation failure, the stage MUST retry (see FR-6) |
| FR-2.3.6 | Any LLM API error MUST be reported as a pipeline failure at stage `SYNTHESIZE` |

### FR-3 — Semantic Knowledge Base

| ID | Requirement |
|---|---|
| FR-3.1 | Past incidents MUST be stored as embeddings in a vector store |
| FR-3.2 | On first startup, the service MUST seed the vector store from `past_incidents.txt` (see §8.2) |
| FR-3.3 | Seeding MUST be skipped if the vector store already contains at least one document (idempotent restart). The presence check MUST use an exact document count query against the collection, not a similarity search. |
| FR-3.4 | Each incident MUST be stored as an independent document with an `incidentId` metadata field |
| FR-3.5 | Similarity search MUST return at most 3 documents per query |
| FR-3.6 | When no documents match, the knowledge layer MUST return the literal string `"No relevant past incidents found."` |
| FR-3.7 | `system_description.txt` MUST be loaded once at startup and cached in memory |
| FR-3.8 | If `system_description.txt` is missing or blank at startup, the service MUST fail to start with a descriptive error |
| FR-3.9 | If `past_incidents.txt` is not found at startup and the vector store is empty, the service MUST fail to start with a descriptive error |

### FR-4 — Knowledge Ingestion API

| ID | Requirement |
|---|---|
| FR-4.1 | The service MUST expose `POST /api/v1/admin/incidents/knowledge` for runtime knowledge ingestion |
| FR-4.2 | The request body MUST be JSON with required non-blank string fields `incidentId` and `text` |
| FR-4.3 | On success, the incident MUST be embedded and stored in the vector store immediately; the response MUST be HTTP 201 |
| FR-4.4 | A duplicate `incidentId` MUST replace (upsert) the existing document |
| FR-4.5 | Blank or missing `incidentId` or `text` MUST return HTTP 400 |

### FR-5 — Output Validation

After deserialising the LLM response, the service MUST validate the `IncidentAnalysis` object. The following conditions MUST each raise a validation error:

| ID | Rule |
|---|---|
| FR-5.1 | `category` is absent, null, or blank |
| FR-5.2 | `summary` is absent, null, or blank |
| FR-5.3 | `severity` is absent or null |
| FR-5.4 | `hypotheses` is absent, null, or empty |
| FR-5.5 | `hypotheses` contains more than 3 items |
| FR-5.6 | Any hypothesis has a blank `title` or blank `reasoning` |
| FR-5.7 | Any hypothesis has fewer than 2 or more than 3 `next_steps` |

All validation errors MUST be collected and reported together in a single error message.

### FR-6 — Retry with Prompt Refinement

| ID | Requirement |
|---|---|
| FR-6.1 | Stage 3 MUST retry on any parse or validation failure, up to a configurable maximum number of attempts; the minimum valid value for `maxAttempts` is 2, and the service MUST reject any configured value below 2 at startup |
| FR-6.2 | Each retry user message MUST be constructed by appending `RETRY_SUFFIX_TEMPLATE` (see §7) to the **original** Stage 3 user message (not to a previously appended message); each retry therefore contains exactly one error suffix |
| FR-6.3 | A configurable delay MUST be applied between attempts |
| FR-6.4 | After exhausting all attempts, the service MUST report an unrecoverable pipeline failure at stage `SYNTHESIZE` |

### FR-7 — LLM Provider Configurability

| ID | Requirement |
|---|---|
| FR-7.1 | The LLM provider MUST be selectable via configuration with no code changes |
| FR-7.2 | The API key MUST be supplied via an environment variable; it MUST NOT appear in source code |
| FR-7.3 | The model name MUST be configurable, with a default value |

### FR-8 — Health Check

| ID | Requirement |
|---|---|
| FR-8.1 | The service MUST expose a health check endpoint at `GET /actuator/health` |
| FR-8.2 | The endpoint MUST return HTTP 200 when the application context is running |
| FR-8.3 | The endpoint MUST return a JSON body with at minimum a `status` field |

---

## 4. Non-Functional Requirements

### NFR-1 — Performance

| ID | Requirement |
|---|---|
| NFR-1.1 | End-to-end p95 latency MUST be under 30 seconds under normal LLM API conditions |
| NFR-1.2 | Vector store similarity search MUST complete within 200 ms for a corpus up to 10,000 documents |
| NFR-1.3 | Application startup including vector store seeding MUST complete within 60 seconds |
| NFR-1.4 | The `description` field MUST be limited to a maximum of 2,000 characters; requests exceeding this limit MUST return HTTP 400 |
| NFR-1.5 | Each individual LLM API call MUST have a configurable timeout; the default MUST be 25 seconds; requests exceeding the timeout MUST be treated as LLM API failures |

### NFR-2 — Reliability

| ID | Requirement |
|---|---|
| NFR-2.1 | Every expected failure mode MUST produce a structured error response, never an unhandled exception |
| NFR-2.2 | Stage 3 retries MUST be exhausted before the service returns a 502 |
| NFR-2.3 | A vector store seeding failure MUST prevent startup (fail-fast) |
| NFR-2.4 | A vector store query failure during request processing MUST return HTTP 502 with a structured error body; it MUST NOT cause an unhandled exception |

### NFR-3 — Security

| ID | Requirement |
|---|---|
| NFR-3.1 | The LLM API key MUST NOT appear in source code, build artefacts, or logs at any level |
| NFR-3.2 | Raw incident descriptions MUST NOT be written to persistent log storage at INFO level or above. Writing raw descriptions to DEBUG-level logs is permitted only when DEBUG logging is explicitly enabled; DEBUG logging MUST be disabled by default in the production configuration profile. |

### NFR-4 — Maintainability

| ID | Requirement |
|---|---|
| NFR-4.1 | New past incidents MUST be addable to the vector store at runtime via the admin API (FR-4) without code changes or redeployment |
| NFR-4.2 | All LLM prompt strings MUST be defined in a single dedicated module, not scattered across the codebase |
| NFR-4.3 | All pipeline stages MUST be independently testable in isolation |

### NFR-5 — Observability

| ID | Requirement |
|---|---|
| NFR-5.1 | Each stage completion MUST be logged at DEBUG level |
| NFR-5.2 | Each retry attempt MUST be logged at WARN level with attempt number and error |
| NFR-5.3 | All unhandled exceptions MUST be logged at ERROR level with a full stack trace |
| NFR-5.4 | Raw LLM prompts and responses MUST be logged at DEBUG level only when DEBUG logging is explicitly enabled (see NFR-3.2) |

---

## 5. Data Model

### IncidentRequest — API input

| Field | Type | Constraint |
|---|---|---|
| `description` | string | Required; non-blank; max 2,000 characters |

### IncidentAnalysis — API output and LLM target

| Field | Type | Constraint |
|---|---|---|
| `category` | string | Required; non-blank |
| `summary` | string | Required; non-blank |
| `severity` | Severity | Required |
| `hypotheses` | list of Hypothesis | Required; 1–3 items |

Unknown fields returned by the LLM MUST be tolerated and ignored.

### Hypothesis

| Field | JSON key | Type | Constraint |
|---|---|---|---|
| title | `title` | string | Required; non-blank |
| reasoning | `reasoning` | string | Required; non-blank |
| next steps | `next_steps` | list of string | Required; 2–3 items |

### Severity

Enumeration with three values:

| Value | Meaning |
|---|---|
| `low` | Degraded non-critical path; limited user impact |
| `medium` | Partial failure; subset of users affected |
| `high` | Major failure; payment-critical path down or widespread impact |

Parsing MUST be case-insensitive: `"high"`, `"HIGH"`, and `"High"` are all valid.
Serialisation MUST always produce lowercase values (`"low"`, `"medium"`, `"high"`).

### AnalysisStage

Enumeration used to tag pipeline failures:

| Value | Label |
|---|---|
| `EXTRACT_FACTS` | Extracting facts |
| `ENRICH_CONTEXT` | Enriching context |
| `SYNTHESIZE` | Synthesizing analysis |

### KnowledgeIngestRequest — knowledge ingestion input

| Field | Type | Constraint |
|---|---|---|
| `incidentId` | string | Required; non-blank |
| `text` | string | Required; non-blank |

---

## 6. API Specification

### POST /api/v1/incidents/analyze

**Request:**
```
Content-Type: application/json

{
  "description": "Card payments are failing with timeouts since 12:05 UTC."
}
```

**Response 200:**
```json
{
  "category": "External payment provider issue",
  "summary": "PayGate is not responding in time, causing mass card payment failures.",
  "severity": "high",
  "hypotheses": [
    {
      "title": "PayGate degradation",
      "reasoning": "Timeouts are observed only when calling PayGate; all other services are stable.",
      "next_steps": [
        "Check the PayGate status page for active incidents.",
        "Review payment-service logs for timeout error rates and start timestamps."
      ]
    }
  ]
}
```

### POST /api/v1/admin/incidents/knowledge

**Request:**
```
Content-Type: application/json

{
  "incidentId": "INC-105",
  "text": "[INC-105] Auth service key rotation causing 401 errors\nSymptoms: ...\nRoot cause: ...\nCategory: ..."
}
```

**Response 201:** empty body.

### GET /actuator/health

**Response 200:**
```json
{ "status": "UP" }
```

### Error body format

```json
{
  "error": "short label",
  "detail": "full error message",
  "status": 400
}
```

**Error mapping:**

| Condition | HTTP | `error` value |
|---|---|---|
| `description` blank, missing, or exceeds 2,000 chars | 400 | `"Validation failed"` |
| `incidentId` or `text` blank or missing (ingestion) | 400 | `"Validation failed"` |
| Request body exceeds 16 KB | 400 | `"Validation failed"` |
| Unsupported Content-Type | 415 | `"Unsupported media type"` |
| Unsupported HTTP method | 405 | `"Method not allowed"` |
| Pipeline stage failure | 502 | `"Agent pipeline error"` |
| LLM output unparseable after all retries | 502 | `"LLM response parse error"` |
| Upstream LLM API returned an error | 502 | `"Upstream LLM API error"` |
| Vector store query failure | 502 | `"Knowledge base error"` |
| Unexpected internal error | 500 | `"Internal server error"` |

---

## 7. LLM Prompt Specifications

All prompt strings used at runtime. Placeholders in `<angle_brackets>` are substituted at runtime.

### STAGE1_SYSTEM

```
You are an incident analysis assistant for a payment platform.
Your task is to extract structured facts from a raw incident description.

Extract and return ONLY a JSON object with these fields:
{
  "affected_services": ["list of service names mentioned or implied"],
  "symptoms": ["list of observed symptoms"],
  "error_types": ["list of error types, codes, or keywords"],
  "time_context": "any time reference mentioned, or null"
}

Return raw JSON only. No markdown, no explanation.
```

### STAGE2_SYSTEM

```
You are an incident analysis assistant for a payment platform.
You have access to the system architecture description and a catalogue of past incidents.
Your task is to match the extracted incident facts against this knowledge
and identify the most likely incident category and relevant context.

Return ONLY a JSON object:
{
  "matched_past_incident": "incident ID and title, or null",
  "likely_category": "one-line category label",
  "relevant_services": ["services most likely involved"],
  "context_notes": "brief notes on why this matches the architecture/history"
}

Return raw JSON only. No markdown, no explanation.
```

### STAGE2_USER_TEMPLATE

```
Extracted facts:
<stage1_output>

System architecture:
<system_description>

Past incidents:
<past_incidents>
```

Substitutions:
- `<stage1_output>` — raw JSON string produced by Stage 1
- `<system_description>` — content of `system_description.txt`
- `<past_incidents>` — top-3 semantically similar past incidents retrieved from the vector store

### STAGE3_SYSTEM

```
You are an incident analysis assistant for a payment platform.
Using the structured facts and enriched context provided, generate a final incident analysis.

You MUST return a valid JSON object matching this exact schema:
{
  "category": "string — concise incident category",
  "summary": "string — 2-3 sentences: what is happening, who is affected, urgency",
  "severity": "low | medium | high",
  "hypotheses": [
    {
      "title": "string — hypothesis name",
      "reasoning": "string — why this is plausible given the facts",
      "next_steps": ["string", "string", "string"]
    }
  ]
}

Rules:
- severity must be exactly one of: low, medium, high
- hypotheses must contain 1 to 3 items
- next_steps must contain 2 to 3 items per hypothesis
- Return raw JSON only. No markdown fences, no explanation, no extra keys.
```

### STAGE3_USER_TEMPLATE

```
Extracted facts:
<stage1_output>

Enriched context:
<stage2_output>

Generate the final incident analysis JSON now.
```

Substitutions:
- `<stage1_output>` — raw JSON string produced by Stage 1
- `<stage2_output>` — raw JSON string produced by Stage 2

### RETRY_SUFFIX_TEMPLATE

Appended to the **original** Stage 3 user message when a retry is triggered:

```

Your previous response failed validation with these errors:
<validation_errors>

Please return ONLY valid JSON matching the required schema. No markdown, no explanation.
```

Substitution:
- `<validation_errors>` — joined list of validation error messages from the failed attempt

---

## 8. Knowledge Base Content

### 8.1 system_description.txt

Stored at `src/main/resources/knowledge/system_description.txt`. Loaded verbatim into Stage 2 prompts.

```
The payment platform is composed of six services that communicate over internal HTTP.

The api-gateway service receives all external HTTP requests from clients and routes them to the appropriate internal services. It is the single entry point for all client traffic.

The auth-service handles authentication and issues JWT tokens. It is responsible for verifying credentials and signing tokens used by other services to authorise requests.

The payment-service is responsible for creating and processing payment transactions. It calls external payment providers such as PayGate to execute card payments. This service has its own dedicated PostgreSQL database instance. External provider errors — including timeouts, HTTP 5xx responses, and invalid credential errors — are common and must be handled gracefully.

The billing-service manages customer balances and invoicing. It maintains its own separate PostgreSQL database instance and is not directly involved in payment provider calls.

The notification-service sends e-mail and SMS notifications to customers. It depends on external SMTP and SMS provider APIs. When those external providers experience degradation, notification delivery fails while the rest of the platform continues to function normally.

The reporting-service generates analytical reports and exports. It runs long-running queries directly against the payment-service PostgreSQL instance. These queries can cause significant CPU and I/O load on the database, resulting in degraded performance for the payment-service during report generation.

All six services write structured logs to a centralised ELK stack (Elasticsearch, Logstash, Kibana), which is the primary source of truth for log-based diagnostics.
```

### 8.2 past_incidents.txt

Stored at `src/main/resources/knowledge/past_incidents.txt`. Parsed at startup into individual documents seeded into the vector store.

Format: one block per incident, split on `[INC-NNN]` prefix markers.

```
[INC-101] External payment provider timeout causing card payment failures
Symptoms: Customers unable to pay by card. Transactions failing in bulk.
Root cause: Massive timeouts observed in payment-service logs when calling the PayGate provider. Started around 12:05 UTC. No anomalies in any other service.
Category: External payment provider issue

[INC-102] Reporting-service DB load causing payment latency and gateway timeouts
Symptoms: Sharp increase in response time for /payments/create (5-7 seconds). Some customers receiving 504 Gateway Timeout from api-gateway.
Root cause: DB dashboards showed high CPU and many long-running queries originating from reporting-service, overloading the shared payment-service PostgreSQL instance.
Category: DB degradation caused by reporting

[INC-103] SMTP provider failure causing missing top-up confirmation e-mails
Symptoms: Users not receiving top-up confirmation e-mails. Money credited correctly, balances accurate.
Root cause: Intermittent connection errors to the SMTP provider in notification-service logs. Payment processing unaffected.
Category: Notification delivery issue

[INC-104] Invalid token signatures causing mobile login failures
Symptoms: Some customers unable to log in via mobile app. auth-service returning 401 errors.
Root cause: Logs show messages about invalid token signatures. No large-scale failures in other services. Isolated to auth-service.
Category: User authentication errors
```

---

## 9. Output JSON Schema

Stored at `src/main/resources/schemas/incident_analysis_schema.json`. Used as a fallback validation source when the LLM response cannot be deserialised directly.

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["category", "summary", "severity", "hypotheses"],
  "additionalProperties": true,
  "properties": {
    "category": {
      "type": "string",
      "minLength": 1
    },
    "summary": {
      "type": "string",
      "minLength": 1
    },
    "severity": {
      "type": "string",
      "enum": ["low", "medium", "high"]
    },
    "hypotheses": {
      "type": "array",
      "minItems": 1,
      "maxItems": 3,
      "items": {
        "type": "object",
        "required": ["title", "reasoning", "next_steps"],
        "additionalProperties": true,
        "properties": {
          "title": { "type": "string", "minLength": 1 },
          "reasoning": { "type": "string", "minLength": 1 },
          "next_steps": {
            "type": "array",
            "minItems": 2,
            "maxItems": 3,
            "items": { "type": "string", "minLength": 1 }
          }
        }
      }
    }
  }
}
```

---

## 10. Test Scenarios

Language-agnostic test cases. All collaborators (LLM client, vector store) are mocked or stubbed.

### 10.1 Response Parser

Unit tests — no HTTP, no LLM calls.

| # | Scenario | Input | Expected |
|---|---|---|---|
| P-1 | Valid JSON parses correctly | Well-formed JSON matching the schema | Returns populated `IncidentAnalysis` with all fields set |
| P-2 | Markdown fences are stripped | Valid JSON wrapped in ` ```json\n...\n``` ` | Parses successfully as if fences were absent |
| P-3 | Plain invalid JSON throws | `"not json at all"` | Throws parse/validation error |
| P-4 | Missing severity throws | Valid JSON with `"severity"` key absent | Throws parse/validation error |
| P-5 | Empty hypotheses array throws | Valid JSON with `"hypotheses": []` | Throws parse/validation error |
| P-6 | Case-insensitive severity accepted | `"severity": "HIGH"` | Parsed as the high severity value without error |
| P-7 | Blank category throws | Valid JSON with `"category": ""` | Throws parse/validation error |
| P-8 | Blank summary throws | Valid JSON with `"summary": ""` | Throws parse/validation error |

Provide a reusable helper returning the canonical valid JSON so individual tests only modify what they need.

**Canonical valid JSON for tests:**
```json
{
  "category": "External payment provider issue",
  "summary": "PayGate is not responding in time, causing mass card payment failures.",
  "severity": "high",
  "hypotheses": [
    {
      "title": "PayGate degradation",
      "reasoning": "Timeouts only on PayGate calls; other services stable.",
      "next_steps": ["Check PayGate status page", "Review payment-service error logs"]
    }
  ]
}
```

### 10.2 Agent Pipeline

Unit tests — LLM client and knowledge base are mocked.

**Reusable mock responses:**

Stage 1 mock output:
```json
{"affected_services":["payment-service"],"symptoms":["timeouts"],"error_types":["timeout"],"time_context":null}
```

Stage 2 mock output:
```json
{"matched_past_incident":"INC-101","likely_category":"External payment provider issue","relevant_services":["payment-service"],"context_notes":"matches INC-101"}
```

Stage 3 valid mock output: use canonical valid JSON from §10.1.

| # | Scenario | Mock setup | Expected |
|---|---|---|---|
| A-1 | Happy path | Stages 1+2 return mock outputs above; Stage 3 returns valid analysis | Returns `IncidentAnalysis` with `category` = `"External payment provider issue"` |
| A-2 | Retry on parse failure, then succeeds | Stage 3 first call produces unparseable output; second call produces valid structured output | Returns valid `IncidentAnalysis`; Stage 3 was called exactly twice |
| A-3 | Retry on validation failure, then succeeds | Stage 3 first call returns an `IncidentAnalysis` with null `severity`; second call returns valid output | Returns valid `IncidentAnalysis` with all required fields present |
| A-4 | Retry message contains validation errors | Same setup as A-3 | The second Stage 3 user message contains the word `"validation"` |
| A-5 | Exhausts retries and throws | All Stage 3 calls return unparseable content | Throws pipeline error identifying `SYNTHESIZE` as the failed stage |
| A-6 | Stage 1 LLM failure propagates | Stage 1 throws upstream LLM error | Throws pipeline error identifying `EXTRACT_FACTS` as the failed stage |

For test A-5, set `maxAttempts = 3` and `backoffDelayMs = 0`.

### 10.3 REST Controller

Slice/integration tests — only the HTTP layer loaded; pipeline is mocked.

| # | Scenario | Setup | Expected HTTP |
|---|---|---|---|
| C-1 | Valid request, pipeline succeeds | Pipeline returns valid `IncidentAnalysis` | 200; body contains `"category"` key |
| C-2 | Blank description | `{"description": ""}` | 400; body contains `"error"` key |
| C-3 | Missing description field | `{}` | 400; body contains `"error"` key |
| C-4 | Pipeline throws pipeline error | Mock throws pipeline exception | 502; `"error"` = `"Agent pipeline error"` |
| C-5 | Pipeline throws parse error | Mock throws parse/validation exception | 502; `"error"` = `"LLM response parse error"` |
| C-6 | Description exceeds 2,000 characters | Body with `description` of 2,001 characters | 400; body contains `"error"` key |

### 10.4 Knowledge Base

Unit tests — vector store is mocked.

| # | Scenario | Mock setup | Expected |
|---|---|---|---|
| K-1 | Returns relevant past incidents | Vector store returns 2 documents | Result string contains the text of both documents |
| K-2 | Fallback on empty results | Vector store returns empty list | Returns `"No relevant past incidents found."` |
| K-3 | Returns system description | — | Returns the content of `system_description.txt` |
| K-4 | Missing system description file | File does not exist | Startup fails with descriptive error |

### 10.5 Past Incident Seeder

Unit tests — vector store is mocked.

| # | Scenario | Setup | Expected |
|---|---|---|---|
| S-1 | Parses incident blocks correctly | Input: content of `past_incidents.txt` | 4 documents; each with correct `incidentId` metadata |
| S-2 | Skips empty blocks | Content with leading blank lines before first `[INC-` | No empty documents produced |
| S-3 | Skips seeding when already seeded | Vector store count query returns value > 0 | `add()` is never called |

### 10.6 Knowledge Ingestion Controller

Slice/integration tests — knowledge base is mocked.

| # | Scenario | Setup | Expected HTTP |
|---|---|---|---|
| I-1 | Valid request, ingestion succeeds | Knowledge base `add()` returns normally | 201; empty body |
| I-2 | Blank incidentId | `{"incidentId": "", "text": "..."}` | 400; body contains `"error"` key |
| I-3 | Missing text field | `{"incidentId": "INC-105"}` | 400; body contains `"error"` key |

### 10.7 Application Context

Integration test — full application context loads.

| # | Scenario | Expected |
|---|---|---|
| X-1 | Context loads with dummy credentials | Application context starts without errors; vector store dependency is satisfied by a mock/stub |

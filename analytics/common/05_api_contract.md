# Common 05 — REST API Contract

Language-agnostic specification of the HTTP interface.
Stack-specific prompts implement this contract using their framework's routing and serialisation.

---

## Endpoint

### `POST /api/v1/incidents/analyze`

Analyzes an incident description and returns a structured analysis.

#### Request

- Content-Type: `application/json`
- Body:

```json
{
  "description": "string — raw incident description text, required, non-blank"
}
```

Validation errors on the request body must return HTTP 400 (see error responses below).

#### Response — 200 OK

- Content-Type: `application/json`
- Body: `IncidentAnalysis` (see `common/01_domain_concepts.md`)

```json
{
  "category": "string",
  "summary": "string",
  "severity": "low|medium|high",
  "hypotheses": [
    {
      "title": "string",
      "reasoning": "string",
      "next_steps": ["string", "string"]
    }
  ]
}
```

---

## Error response format

All error responses share the same body shape:

```json
{
  "error": "string — short error label",
  "detail": "string — full error message",
  "status": 400
}
```

---

## Error mapping

| Condition | HTTP Status | `error` value |
|---|---|---|
| Request body validation failure (blank/missing description) | 400 | `"Validation failed"` |
| Agent pipeline stage failure | 502 | `"Agent pipeline error"` |
| LLM output could not be parsed after retries | 502 | `"LLM response parse error"` |
| Upstream LLM API returned an error | 502 | `"Upstream LLM API error"` |
| Any other unexpected error | 500 | `"Internal server error"` |

All handled errors must be logged at ERROR level with the full stack trace.

# Common 01 — Domain Concepts

Language-agnostic specification of all domain types the service uses.
Stack-specific prompts implement these in their language's type system.

---

## Domain entities

### IncidentRequest

The input to the service. Carries a single field:

| Field | Type | Constraint |
|---|---|---|
| `description` | string | required, non-blank |

### Severity

An enumeration of incident criticality levels:

| Value | Meaning |
|---|---|
| `low` | Degraded non-critical path; limited user impact |
| `medium` | Partial failure; subset of users affected |
| `high` | Major failure; payment-critical path down or widespread user impact |

Parsing must be case-insensitive: `"high"`, `"HIGH"`, and `"High"` are all valid representations of the same value.

### Hypothesis

One plausible root-cause hypothesis.

| Field | Type | Constraint |
|---|---|---|
| `title` | string | non-blank; one-line label |
| `reasoning` | string | non-blank; explains why this is plausible |
| `next_steps` | list of strings | 2–3 items; concrete diagnostic actions |

### IncidentAnalysis

The full structured response returned by the service.

| Field | Type | Constraint |
|---|---|---|
| `category` | string | non-blank; concise incident category label |
| `summary` | string | non-blank; 2–3 sentences describing what is happening, who is affected, and urgency |
| `severity` | Severity | required |
| `hypotheses` | list of Hypothesis | 1–3 items |

Unknown fields from the LLM must be tolerated (ignored), not cause a parse error.

### AnalysisStage

An enumeration of the three pipeline stages, each with a human-readable label:

| Constant | Label |
|---|---|
| `PARSE_INPUT` | `"Parsing input"` |
| `ENRICH_WITH_CONTEXT` | `"Enriching with context"` |
| `GENERATE_RESPONSE` | `"Generating response"` |

---

## JSON output format

The canonical wire format for `IncidentAnalysis` (used in both API responses and LLM output):

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

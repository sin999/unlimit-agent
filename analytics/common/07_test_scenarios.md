# Common 07 — Test Scenarios

Language-agnostic description of all test cases.
Stack-specific prompts implement these using their testing framework.

---

## Response parser tests

Unit-level tests — no HTTP, no Spring context, no mocked LLM.

| Scenario | Input | Expected outcome |
|---|---|---|
| Valid JSON parses correctly | A well-formed JSON string matching the output schema | Returns a populated `IncidentAnalysis` with all fields set |
| Markdown fences are stripped | Valid JSON wrapped in ` ```json\n...\n``` ` | Parses successfully as if fences were absent |
| Plain invalid JSON throws | `"not json at all"` | Throws `ResponseParseException` |
| Missing `severity` field throws | Valid JSON with the `"severity"` key absent | Throws `ResponseParseException` |
| Empty `hypotheses` array throws | Valid JSON with `"hypotheses": []` | Throws `ResponseParseException` |
| Case-insensitive severity accepted | `"severity": "HIGH"` | Parses to `Severity.HIGH` without error |

Provide a reusable helper that returns the canonical valid JSON string so individual tests
only need to specify what they change.

---

## Agent pipeline tests

Unit-level tests — `LlmClient` and `KnowledgeBase` are mocked/stubbed.

| Scenario | Setup | Expected outcome |
|---|---|---|
| Happy path | All three `complete()` calls return valid mocked JSON for their respective stages | Returns `IncidentAnalysis` with the expected `category` |
| Retry on parse failure, then succeeds | Stage 3 first call returns invalid JSON; second call returns valid JSON | Returns `IncidentAnalysis`; verify `complete()` was called exactly twice for Stage 3 |
| Exhausts retries and throws | Stage 3 always returns invalid JSON (all attempts) | Throws the pipeline's error type |
| Stage 1 upstream failure propagates | Stage 1 `complete()` throws `LlmApiException` | Throws the pipeline's error type, identifying `PARSE_INPUT` as the failed stage |

For the retry scenario: use argument capture to verify the second Stage 3 call contains
the validation error text in the user message (the retry refinement suffix).

---

## REST controller / HTTP layer tests

Integration-level tests — only the HTTP layer is loaded; the pipeline is mocked/stubbed.

| Scenario | Setup | Expected HTTP response |
|---|---|---|
| Valid request, pipeline succeeds | Pipeline returns a valid `IncidentAnalysis` | `200 OK`; body contains `"category"` key |
| Blank description | POST body `{"description": ""}` | `400 Bad Request`; body contains `"error"` key |
| Missing description field | POST body `{}` | `400 Bad Request` |
| Pipeline throws its error type | Pipeline mock throws the pipeline exception | `502`; body `"error"` == `"Agent pipeline error"` |
| Parser throws its error type | Pipeline mock throws the parse exception | `502`; body `"error"` == `"LLM response parse error"` |

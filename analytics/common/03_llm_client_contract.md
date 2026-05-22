# Common 03 — LLM Client Contract

Language-agnostic specification of the LLM client abstraction.
Stack-specific prompts implement this interface and its supporting types.

---

## Design principle

All pipeline code depends on the `LlmClient` abstraction only.
The concrete implementation is the default active provider.
Swapping to a different LLM provider means implementing `LlmClient` for that provider
and updating environment variables — zero changes to pipeline logic.

---

## LlmClient interface

A single method:

```
complete(systemPrompt: string, userMessage: string) -> string
```

- `systemPrompt` — the system/context turn sent to the model
- `userMessage` — the user turn to complete
- Returns — the model's text response (raw string)
- Throws `LlmApiException` on any upstream error (HTTP error, network failure, etc.)

---

## LlmApiException

Carries:

| Field | Type |
|---|---|
| `httpStatus` | integer |
| `rawBody` | string |

Message format: `"LLM API error [<httpStatus>]: <rawBody>"`

---

## Configuration properties

The implementation requires these externally-supplied values:

| Key | Env variable | Description |
|---|---|---|
| `llm.api.key` | `LLM_API_KEY` | API authentication key |
| `llm.api.base-url` | `LLM_API_BASE_URL` | Provider base URL (e.g. `https://api.anthropic.com`) |
| `llm.api.model` | `LLM_API_MODEL` | Model identifier (e.g. `claude-sonnet-4-6`, `gpt-4o`) |
| `llm.api.max-tokens` | — | Maximum tokens in the response (default: `2048`) |
| `llm.api.provider-version` | `LLM_API_PROVIDER_VERSION` | Optional provider-specific API version header value |

All values must come from environment variables or external config — never hardcoded.

---

## Included Anthropic implementation

The default implementation targets the Anthropic Messages API:

- Endpoint: `POST /v1/messages`
- Auth headers: `x-api-key: <key>` and, if `providerVersion` is set, `anthropic-version: <providerVersion>`
- Request body shape:
  ```json
  {
    "model": "<model>",
    "max_tokens": <maxTokens>,
    "system": "<systemPrompt>",
    "messages": [{ "role": "user", "content": "<userMessage>" }]
  }
  ```
- Response: extract `content[0].text`

To add a new provider (e.g. OpenAI), implement `LlmClient` using the OpenAI Chat Completions API
(`POST /v1/chat/completions`) and make it the active implementation via configuration.

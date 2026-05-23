# Business Requirements Document
## AI Incident Assistant

**Version:** 1.2  
**Date:** 2026-05-23  
**Status:** Approved

---

## 1. Executive Summary

On-call engineers at the payment platform spend significant time during incidents manually correlating logs, recalling similar past events, and forming hypotheses under pressure. This document defines the business requirements for an **AI Incident Assistant** — an internal tool that accepts a free-text incident description and returns a structured triage analysis: classification, severity, and ranked diagnostic hypotheses with concrete next steps.

The assistant leverages an LLM within a multi-stage agent architecture and a vector knowledge base of past incidents to ensure consistent, context-aware output.

---

## 2. Business Context and Problem Statement

### 2.1 Context

The payment platform operates six interconnected services (api-gateway, auth-service, payment-service, billing-service, notification-service, reporting-service). Incidents occur regularly due to external provider degradation, database contention, authentication failures, and notification delivery issues.

### 2.2 Problem

| Pain Point | Impact |
|---|---|
| Engineers must manually recall similar past incidents | Inconsistent triage quality; depends on individual memory |
| No structured first-response checklist | Diagnostic steps vary per engineer; some paths missed |
| High cognitive load during high-severity incidents | Slower mean-time-to-diagnosis (MTTD) |
| Institutional knowledge locked in individual engineers | Knowledge lost when team members rotate or leave |

### 2.3 Opportunity

An AI assistant that retrieves semantically similar past incidents and generates structured hypotheses can reduce MTTD, level up junior engineers, and codify institutional knowledge into a queryable system.

---

## 3. Stakeholders

| Stakeholder | Role | Interest |
|---|---|---|
| On-call engineers | Primary users | Fast, accurate triage guidance during incidents |
| Engineering manager | Sponsor | Reduced MTTD; consistent incident response quality |
| Platform / SRE team | Maintainers | Operational simplicity; easy knowledge base updates |
| Security team | Reviewers | No sensitive data leaked to external LLM APIs |

---

## 4. Business Objectives

| ID | Objective | Metric |
|---|---|---|
| BO-1 | Reduce mean time to first hypothesis | Target: engineer reaches first actionable hypothesis within 60 seconds of incident report |
| BO-2 | Ensure consistent triage quality | Target: same incident description produces equivalent severity and category regardless of which engineer submits it |
| BO-3 | Surface relevant past incidents automatically | Target: ≥1 semantically relevant past incident matched for 80% of queries against the known incident corpus. **Evaluation gate:** a minimum corpus of 20 incidents is required before this target is formally evaluated; the 4-incident seed corpus is for bootstrapping and demonstration only. |
| BO-4 | Keep institutional knowledge queryable and up-to-date | New past incidents can be added to the vector knowledge base at runtime via an admin API endpoint, without code changes or container redeployment |

---

## 5. Business Requirements

### 5.1 Core Analysis Requirements

| ID | Requirement |
|---|---|
| BR-1 | The system MUST accept a free-text incident description from an engineer |
| BR-2 | The system MUST classify the incident into a named category (e.g. "External payment provider issue") |
| BR-3 | The system MUST produce a short summary (minimum 2 sentences) describing what is happening, who is affected, and urgency level |
| BR-4 | The system MUST assign a severity level: `low`, `medium`, or `high` |
| BR-5 | The system MUST suggest 1–3 ranked hypotheses about probable root causes |
| BR-6 | Each hypothesis MUST include 2–3 concrete next steps (which logs to check, which metrics to inspect, what action to take) |

### 5.2 Knowledge and Context Requirements

| ID | Requirement |
|---|---|
| BR-7 | The system MUST enrich its analysis using the platform's service architecture description |
| BR-8 | The system MUST retrieve and use past incidents that are semantically similar to the current incident |
| BR-9 | The system MUST expose a runtime API endpoint for adding new incidents to the vector knowledge base without code changes or container redeployment. The `past_incidents.txt` file serves as the initial seed corpus only; ongoing knowledge base growth is handled through this API. |
| BR-10 | Past incident entries MUST record at minimum: symptoms, root cause, and category |

### 5.5 Access Control Requirements

| ID | Requirement |
|---|---|
| BR-17 | When security enforcement is enabled, `POST /api/v1/incidents/analyze` MUST require an authenticated caller with the `user` or `admin` role. |
| BR-18 | When security enforcement is enabled, `POST /api/v1/admin/incidents/knowledge` MUST require an authenticated caller with the `admin` role. |
| BR-19 | The security enforcement mechanism MUST be switchable via a single environment variable (`SECURITY_ENABLED`) so the service can operate without an identity provider in development and CI environments. |

### 5.3 Reliability Requirements

| ID | Requirement |
|---|---|
| BR-11 | The system MUST return a machine-readable structured response on every successful call |
| BR-12 | The system MUST handle LLM output errors (invalid JSON, wrong format) without surfacing raw errors to the user |
| BR-13 | When the LLM returns invalid or malformed structured output, the system MUST attempt recovery by retrying with a refined prompt before returning a failure response. This requirement applies specifically to Stage 3 structured output failures; transient LLM API network errors are not retried in v1. |

### 5.4 Operational Requirements

| ID | Requirement |
|---|---|
| BR-14 | The LLM provider MUST be configurable via environment variables — no credentials hardcoded |
| BR-15 | The system MUST be deployable as a standalone HTTP service |
| BR-16 | The system MUST be operable by a single engineer with minimal infrastructure (one `docker compose up` command) |

---

## 6. Success Criteria

| Criterion | Measurement |
|---|---|
| Correct classification | Category output matches expected category for all 4 canonical past incidents when submitted as new queries |
| Correct severity | Severity matches expected level for high/medium/low canonical test cases |
| Hypothesis quality | Each response contains ≥1 hypothesis with ≥2 actionable next steps |
| Error recovery | System returns structured 502 (not unhandled 500) after exhausting LLM retries |
| Startup reliability | Service starts and ChromaDB seeding completes within 60 seconds on a clean environment |
| Runtime knowledge ingestion | A new incident added via the admin API is returned in subsequent similarity searches without any restart or redeployment |
| MTTD reduction | Pre-rollout baseline MTTD is measured across 10 real incidents; post-rollout MTTD is re-measured after 30 days to evaluate BO-1 |

---

## 7. Assumptions

- Engineers have network access to the configured LLM API endpoint.
- An Anthropic (or equivalent) API key is available and managed by the platform team.
- ChromaDB is hosted as a sidecar container alongside the service.
- The initial knowledge base corpus (4 historical incidents) is sufficient to demonstrate matching; the corpus will grow over time via the admin ingestion API.
- Security enforcement is off by default (`SECURITY_ENABLED=false`). In environments without an identity provider (local dev, CI) no credentials are required. Production deployments MUST set `SECURITY_ENABLED=true` with a configured JWKS or issuer URI.
- Incident descriptions submitted by engineers MUST NOT contain customer PII (names, card numbers, email addresses) or confidential compliance data. Data submitted to the LLM API is governed by the API provider's data processing agreement. The platform team is responsible for communicating this constraint to all users.

---

## 8. Out of Scope (v1)

- Automatic incident creation in ticketing systems (PagerDuty, Jira, etc.)
- Real-time log ingestion or metrics correlation
- Multi-turn conversational interaction (follow-up questions)
- Web UI — HTTP API only
- Automatic post-incident resolution capture

---

## 9. Risk Register

| ID | Risk | Probability | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | LLM API unavailability | Medium | High — full service down; no graceful degradation in v1 | Monitor LLM provider status page; define on-call escalation path if the assistant is unavailable during an incident; plan cached-response fallback for v2 |
| R-2 | HuggingFace ONNX model download failure on first run (~80 MB) | Low | High — service fails to start in restricted or air-gapped network environments | Pre-bake the ONNX model into the Docker image for production deployments; document offline installation procedure |
| R-3 | ChromaDB volume data loss | Low | Medium — knowledge base destroyed; all runtime-added incidents lost; requires re-seeding | Schedule daily backup of the `chroma-data` Docker volume; document recovery procedure and expected RTO |
| R-4 | LLM API rate limiting under concurrent usage | Medium | Medium — requests rejected (HTTP 429) during high-incident periods | Monitor API quota usage; document rate limit thresholds from the provider; configure per-request timeouts |
| R-5 | Sensitive data in incident descriptions sent to external LLM | Medium | High — potential compliance or data-protection violation | Engineer awareness training; evaluate self-hosted LLM (e.g. Ollama) as a v2 option for stricter compliance posture |
| R-6 | Knowledge base corpus too small to achieve BO-3 match rate | High (at launch) | Low — BO-3 metric unmeasurable until corpus grows | Conduct a retroactive incident import sprint before formally measuring BO-3; enforce the 20-incident evaluation gate |
| R-7 | Security toggle left disabled (`SECURITY_ENABLED=false`) in production | Low | High — all endpoints unauthenticated; admin ingestion endpoint exposed | Enforce `SECURITY_ENABLED=true` via IaC and deployment runbooks; the service logs a WARN at startup whenever security is disabled |

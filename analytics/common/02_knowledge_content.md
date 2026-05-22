# Common 02 — Knowledge Base Content

The two static knowledge files the agent uses to enrich its prompts.
These files should be stored verbatim in the project and loaded at startup.

---

## File 1 — System description

**Path:** `src/main/resources/knowledge/system_description.txt`

Write this as clear prose (no bullet lists) suitable for direct inclusion in an LLM prompt:

> The payment platform is composed of six services that communicate over internal HTTP.
>
> The api-gateway service receives all external HTTP requests from clients and routes them to the appropriate internal services. It is the single entry point for all client traffic.
>
> The auth-service handles authentication and issues JWT tokens. It is responsible for verifying credentials and signing tokens used by other services to authorise requests.
>
> The payment-service is responsible for creating and processing payment transactions. It calls external payment providers such as PayGate to execute card payments. This service has its own dedicated PostgreSQL database instance. External provider errors — including timeouts, HTTP 5xx responses, and invalid credential errors — are common and must be handled gracefully.
>
> The billing-service manages customer balances and invoicing. It maintains its own separate PostgreSQL database instance and is not directly involved in payment provider calls.
>
> The notification-service sends e-mail and SMS notifications to customers. It depends on external SMTP and SMS provider APIs. When those external providers experience degradation, notification delivery fails while the rest of the platform continues to function normally.
>
> The reporting-service generates analytical reports and exports. It runs long-running queries directly against the payment-service PostgreSQL instance. These queries can cause significant CPU and I/O load on the database, resulting in degraded performance for the payment-service during report generation.
>
> All six services write structured logs to a centralised ELK stack (Elasticsearch, Logstash, Kibana), which is the primary source of truth for log-based diagnostics.

---

## File 2 — Past incidents

**Path:** `src/main/resources/knowledge/past_incidents.txt`

Write in structured plain-text format, one incident per block:

> [INC-101] External payment provider timeout causing card payment failures
> Symptoms: Customers unable to pay by card. Transactions failing in bulk.
> Root cause: Massive timeouts observed in payment-service logs when calling the PayGate provider. Started around 12:05 UTC. No anomalies in any other service.
> Category: External payment provider issue
>
> [INC-102] Reporting-service DB load causing payment latency and gateway timeouts
> Symptoms: Sharp increase in response time for /payments/create (5–7 seconds). Some customers receiving 504 Gateway Timeout from api-gateway.
> Root cause: DB dashboards showed high CPU and many long-running queries originating from reporting-service, overloading the shared payment-service PostgreSQL instance.
> Category: DB degradation caused by reporting
>
> [INC-103] SMTP provider failure causing missing top-up confirmation e-mails
> Symptoms: Users not receiving top-up confirmation e-mails. Money credited correctly, balances accurate.
> Root cause: Intermittent connection errors to the SMTP provider in notification-service logs. Payment processing unaffected.
> Category: Notification delivery issue
>
> [INC-104] Invalid token signatures causing mobile login failures
> Symptoms: Some customers unable to log in via mobile app. auth-service returning 401 errors.
> Root cause: Logs show messages about invalid token signatures. No large-scale failures in other services. Isolated to auth-service.
> Category: User authentication errors

---

## Loading rules

- Both files must be read once at startup and cached in memory.
- If either file is missing or empty, the application must fail to start with a descriptive error.
- The content is included verbatim in LLM prompts at the enrichment stage.

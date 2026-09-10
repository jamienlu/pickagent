# Operations

[English](operations.md) | [简体中文](operations.zh-CN.md)

## Deployment model

Build the default artifact in CI, inject online configuration at process start, and run one isolated `OpenAiOnlineRunner` session per request. Each session owns and closes its SDK client. Keep staging and production credentials, models, budgets, and spending limits separate.

## Required production controls

- Store API keys in a secret manager and rotate them; never place them in source, images, logs, or command history.
- Set organization-side rate and spend limits in addition to local call, tool, duration, timeout, retry, and output-token budgets.
- Put authorization and human approval in front of sensitive `ToolHandler` implementations.
- Replace `InMemoryIdempotencyStore` with durable transactional storage before claiming crash-safe or distributed idempotency.
- Redact user input, model output, tool arguments, tool results, response IDs, and call IDs according to the application's data policy.
- Keep `live` tests manual or in a tightly controlled scheduled environment with a small approved model and spend cap.

## Observable terminal states

Every normal Runtime terminal result includes `RunUsage(modelCalls, toolCalls, elapsed)`.

| Result | Operational interpretation |
| --- | --- |
| `Completed` | Final answer accepted. |
| `Stopped` | A budget or local safety boundary prevented further work. |
| `ModelFailed` | Provider/transport failure mapped to a stable `FailureKind`. |
| `ToolFailed` | An approved handler reported an expected execution failure. |

Track counts and latency by terminal type and `FailureKind`. Alert on authentication, billing/quota, repeated deadline stops, sustained rate limiting, and service overload. Do not use exception messages as metric labels.

## Rollout and rollback

1. Run the release-verification checklist.
2. Deploy to staging with separate credentials and conservative budgets.
3. Exercise only approved, reversible tools; inspect terminal distributions and cost.
4. Canary a small production percentage.
5. Roll back the application artifact or disable the online entry point if authentication, quota, error-rate, latency, or tool-safety thresholds are breached.

The system does not roll back external side effects that completed before a later tool or model failure. Domain handlers must implement compensation where the business requires it.

## Incident capture

Capture artifact version, sanitized configuration, terminal type, `FailureKind`, usage snapshot, provider request correlation metadata allowed by policy, and whether a tool side effect occurred. Never capture the API key or unredacted sensitive payloads.

# Troubleshooting

[English](troubleshooting.md) | [简体中文](troubleshooting.zh-CN.md)

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Maven compiles for Java 17 | A user Maven profile overrides the project release | Use Java 21 and pass `"-P!jdk-17"`; verify `java -version` and `mvn -version`. |
| Online CLI reports a configuration error | Missing, blank, or invalid environment value | Compare the environment with [Configuration](configuration.md); do not print the key. |
| `AUTHENTICATION` | Invalid, revoked, expired, or unauthorized credential | Rotate/replace the secret and verify project access. Do not retry automatically. |
| `BILLING_OR_QUOTA` | Credit, billing, or organization quota failure | Check provider billing and spend limits. Do not retry automatically. |
| `TRANSIENT_RATE_LIMIT` | Request or token rate limit | Reduce concurrency/load or honor SDK retry policy; inspect organization limits. |
| `TIMEOUT` | Transport timeout | Inspect network and provider latency; tune `OPENAI_TIMEOUT_SECONDS` within the total run deadline. |
| `SERVICE_OVERLOADED` | Provider 5xx/overload | Retry only within the single SDK-owned retry budget; consider degrading or stopping. |
| `INVALID_REQUEST` | Unsupported model, malformed request, or invalid endpoint | Verify model, base URL, tool schema, and request options. |
| `MODEL_CALL_LIMIT` or `TOOL_CALL_LIMIT` | Cross-turn budget exhausted | Inspect the trace for a loop; raise limits only after fixing behavior and estimating cost. |
| `DEADLINE_EXCEEDED` | Total run deadline reached | Compare request timeout/retries with `AGENT_MAX_RUN_SECONDS`; reduce work or increase the deadline deliberately. |
| `DUPLICATE_CALL_ID` | A call ID was replayed within one run | Treat as a protocol/integration defect; do not execute the side effect again. |
| `UNKNOWN_TOOL` or `INVALID_ARGUMENTS` | Model output violates the local allowlist/schema | Fix tool exposure or prompt/schema; keep fail-closed behavior. |
| Live test is skipped | Credentials or model are absent | Expected for safe builds. Provide both values only for an explicitly approved live run. |
| Coverage check fails | A production branch or line lacks a behavior test | Add an assertion-rich test; do not add broad JaCoCo exclusions. |

When escalating, include the artifact version, sanitized configuration, command, terminal type, failure classification, and usage snapshot. Exclude secrets and sensitive payloads.

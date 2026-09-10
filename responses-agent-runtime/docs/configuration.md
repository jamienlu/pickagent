# Configuration

[English](configuration.md) | [简体中文](configuration.zh-CN.md)

Online configuration is read only from the process environment. The default build and the `offline` profile do not read credentials or contact a network service.

## Environment variables

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | Online only | none | API credential. Never log, persist, or commit it. |
| `OPENAI_MODEL` | Online only | none | Model identifier used by every Responses request. |
| `OPENAI_INSTRUCTIONS` | No | absent | Instructions repeated on every continuation request. |
| `OPENAI_BASE_URL` | No | SDK default | Optional compatible endpoint root. Omit for OpenAI. |
| `OPENAI_TIMEOUT_SECONDS` | No | `30` | Positive timeout for one SDK request. |
| `OPENAI_MAX_RETRIES` | No | `2` | Non-negative retry count owned by the SDK transport. |
| `OPENAI_MAX_OUTPUT_TOKENS` | No | `1024` | Positive per-response output-token ceiling. |
| `OPENAI_STORE` | No | `true` | Enables server-side state needed by `previous_response_id`. |
| `AGENT_MAX_MODEL_CALLS` | No | `8` | Positive cross-turn model-call budget. |
| `AGENT_MAX_TOOL_CALLS` | No | `4` | Non-negative application tool-call budget. |
| `AGENT_MAX_RUN_SECONDS` | No | `120` | Positive monotonic run deadline. |

Invalid or blank required values fail before the first request. `OpenAiOnlineConfig.toString()` masks the key and instructions, but callers must still avoid serializing configuration objects into telemetry.

## Source layout and execution profiles

All production code uses `src/main/java`; all tests and test fixtures use `src/test/java`. Profiles never add nonstandard source directories.

| Selection | Execution responsibility | Network behavior |
| --- | --- | --- |
| default | Compiles all production and test code; Surefire runs `*Test`. | Deterministic tests only; no credentials or network. |
| `offline` | Selects `OfflineResponsesDemo` for `exec:java`. | Deterministic local demo; no credentials or network. |
| `online` | Selects `OpenAiAgentCli` for `exec:java`. | A request occurs only when the CLI is explicitly executed. |
| `live` | Enables Failsafe execution of `OpenAiLiveSmokeIT`. | The test skips unless credentials and a model are provided. |

Profiles isolate execution, not source placement. Do not activate `live` in an ordinary CI job.

## Safe examples

```powershell
mvn "-P!jdk-17" clean verify
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
```

Run an online request only from an approved environment with secret injection and cost controls:

```powershell
$env:OPENAI_API_KEY = '<injected by secret manager>'
$env:OPENAI_MODEL = '<approved model>'
mvn "-P!jdk-17,online" -Dexec.args='"hello"' compile exec:java
```

## Retry ownership

The SDK client is the only component that retries transport requests. Runtime budgets count logical model calls, while `OPENAI_MAX_RETRIES` bounds attempts inside one transport call. Adding another retry loop around `OpenAiOnlineRunner` would multiply traffic and must not be done without a new end-to-end retry-budget design.

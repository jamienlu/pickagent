# Responses Agent Runtime

[English](README.md) | [简体中文](README.zh-CN.md)

Java 21 implementation of a bounded Agent Runtime with an OpenAI Responses API protocol adapter. The project keeps provider-neutral runtime types separate from OpenAI SDK objects, validates complete tool-call batches before side effects, executes calls deterministically, and preserves protocol items required for continuation.

## What is included

- A provider-neutral Agent loop with explicit state transitions and step limits.
- Strict function-tool schema mapping for the OpenAI Responses API.
- Ordered mapping of one or more `function_call` output items.
- A protocol ledger that preserves reasoning, assistant messages, calls, and matching outputs.
- A blocking Responses transport boundary and a stateful `AgentModelPort` adapter using `previous_response_id`.
- Per-run model factories that isolate provider conversation state across Runtime runs.
- Cross-turn model-call, tool-call, and monotonic-duration budgets with immutable usage snapshots on every terminal result.
- A trusted tool registry with side-effect-free batch preflight.
- Deterministic serial batch execution with fail-closed error handling.
- Pure retry decisions with exponential backoff, `Retry-After`, jitter, and a total wait budget.
- In-memory idempotency semantics for deterministic local verification.
- Typed OpenAI SDK failure mapping and a single SDK-owned transport retry boundary.
- External online configuration for credentials, model, timeout, retries, output limits, continuation storage, and Runtime budgets.
- Standard Maven source layout: every production class is under `src/main/java`, and every test or test fixture is under `src/test/java`.
- Execution-isolated offline, online, and live profiles; no API key, network, or paid request is required for the default build.
- 181 default tests with enforced 100% line and branch coverage, architecture and source-layout boundaries, and warning-free Javadoc.

## Package boundaries

```text
io.github.jamielu.agent.api
    Provider-neutral records and lifecycle types only

io.github.jamielu.agent.runtime
    Agent loop, model port, state transitions, global budgets, and usage

io.github.jamielu.agent.tool
    Tool allowlist, contract validation, preflight, and dispatch

io.github.jamielu.agent.openai
    OpenAI SDK mapping, transport boundary, conversation state, decoding,
    typed error mapping, and Responses protocol continuation ledger

io.github.jamielu.agent.online
    Validated external configuration, SDK client creation, resource lifecycle,
    and the explicit online CLI

io.github.jamielu.agent.offline
    Deterministic executable proof that never opens a network transport

io.github.jamielu.agent.reliability
    Retry and idempotency policies

src/test/java/io/github/jamielu/agent/live
    Opt-in credentialed smoke test, excluded from ordinary Surefire execution
```

The runtime and API packages never import OpenAI SDK types. Dependency direction is from adapters toward the provider-neutral API, not from the runtime toward a provider.

## Agent loop and Responses adapters

`AgentRuntime` implements the complete provider-neutral loop: it can make repeated
model decisions, execute one validated tool call per step, and stop on a final
answer or an explicit budget/safety condition.

`OpenAiResponsesModel` coordinates `OpenAiConversation`, `OpenAiResponseDecoder`, and the injectable `OpenAiResponsesTransport`. The first turn sends user text; later turns send the latest matching `function_call_output` with the previous response ID. Model, instructions, tools, output limits, and storage options are applied consistently; `parallel_tool_calls=false` keeps each provider decision compatible with the core port's zero-or-one-call contract. `OpenAiOnlineRunner` creates and closes one SDK-backed session per online run.

`RunBudget` limits model calls, application tool calls, and total monotonic duration across the entire loop. Checks occur before model requests and tool side effects. `Completed`, `Stopped`, `ModelFailed`, and `ToolFailed` all include an immutable `RunUsage` snapshot.

`OpenAiResponseReplay` remains the protocol-focused offline adapter. It proves one
Responses tool batch plus its immediate continuation turn:

```text
response.output
    -> validate complete protocol history
    -> map all function calls
    -> preflight the complete call batch
    -> execute prepared calls serially in response order
    -> append matching function_call_output items
    -> invoke the supplied next-turn fixture
    -> require that turn to contain the final assistant answer
```

For a batch `reasoning, C1, C2`, the continuation is `reasoning, C1, C2, O1, O2`. An invalid second call prevents the first handler from running. An execution failure stops later calls and does not invoke the next model turn; already completed external side effects are not rolled back.

## Build and run

```powershell
$env:JAVA_HOME = 'C:\sdk\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn "-P!jdk-17" clean verify
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
```

The offline demo prints `ledger.proof=PASS` with two model calls and one tool call. Online verification and live compilation do not send requests. See [Configuration](docs/configuration.md) before any explicitly approved online run.

## Current scope

- Function arguments are currently restricted to required string fields.
- Multiple calls are preflighted together and then executed serially; no thread-pool parallelism is implemented.
- The blocking SDK transport and multi-step Runtime adapter are implemented, but the default build and offline demo never make a live request.
- The live adapter deliberately disables parallel tool calls; `OpenAiResponseReplay` retains offline coverage for deterministic multi-call batches.
- A credentialed smoke test lives in the standard test tree but executes only behind the explicit `live` profile; it is not run during ordinary verification and does not prove production latency, cost, or model behavior.
- The idempotency store is process-local and does not provide crash recovery or distributed exactly-once semantics.
- Authorization and human approval must be added by the application before sensitive handlers execute.

## Architecture decisions

- [Documentation index](docs/README.md)
- [Architecture, class responsibilities, and call paths](docs/architecture.md)
- [Sustainable project mind map](docs/project-mind-map.md)
- [Configuration](docs/configuration.md)
- [Operations](docs/operations.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Testing](docs/testing.md)
- [Release verification](docs/release-verification.md)
- [Project and OpenAI recall questions with answers](docs/project-recall-qa.md)
- [ADR-0001: Runtime and package boundaries](docs/adr/0001-runtime-and-package-boundaries.md)
- [ADR-0002: Responses protocol ledger and call batches](docs/adr/0002-responses-protocol-ledger.md)
- [ADR-0003: Tool contracts and execution authority](docs/adr/0003-tool-contract-and-authority.md)
- [ADR-0004: Retry and idempotency policy](docs/adr/0004-retry-and-idempotency.md)
- [ADR-0005: Response terminal and decoding boundaries](docs/adr/0005-response-terminal-and-decoding.md)
- [ADR-0006: Offline verification boundary](docs/adr/0006-offline-verification-boundary.md)
- [ADR-0007: Stateful Responses continuation](docs/adr/0007-stateful-responses-continuation.md)
- [ADR-0008: Global run budget](docs/adr/0008-global-run-budget.md)
- [ADR-0009: Profile isolation and responsibility split](docs/adr/0009-profile-isolation-and-responsibility-split.md)
- [ADR-0010: Standard Maven source layout](docs/adr/0010-standard-maven-source-layout.md)

## Official references

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Reasoning models](https://developers.openai.com/api/docs/guides/reasoning)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Error codes](https://developers.openai.com/api/docs/guides/error-codes)
- [OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)
- [OpenAI Production best practices](https://developers.openai.com/api/docs/guides/production-best-practices)

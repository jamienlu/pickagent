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
- A trusted tool registry with side-effect-free batch preflight.
- Deterministic serial batch execution with fail-closed error handling.
- Pure retry decisions with exponential backoff, `Retry-After`, jitter, and a total wait budget.
- In-memory idempotency semantics for deterministic local verification.
- Offline SDK fixtures and contract tests; no API key is required for the default build.

## Package boundaries

```text
io.github.jamielu.agent.api
    Provider-neutral records and lifecycle types only

io.github.jamielu.agent.runtime
    Agent loop, model port, state transitions, and stop budgets

io.github.jamielu.agent.tool
    Tool allowlist, contract validation, preflight, and dispatch

io.github.jamielu.agent.openai
    OpenAI SDK mapping, live transport bridge, stateful model adapter,
    and Responses protocol continuation ledger

io.github.jamielu.agent.reliability
    Retry and idempotency policies

io.github.jamielu.agent.example
    Reproducible offline entry point and fixture adapters
```

The runtime and API packages never import OpenAI SDK types. Dependency direction is from adapters toward the provider-neutral API, not from the runtime toward a provider.

## Agent loop and Responses adapters

`AgentRuntime` implements the complete provider-neutral loop: it can make repeated
model decisions, execute one validated tool call per step, and stop on a final
answer or an explicit budget/safety condition.

`OpenAiResponsesModel` connects that loop to `responses.create` through the injectable `OpenAiResponsesTransport`. The first turn sends user text; later turns send the latest matching `function_call_output` with the previous response ID. Model, instructions, and tools are repeated every turn. `store=true` enables server-side continuation and `parallel_tool_calls=false` keeps each provider decision compatible with the core port's zero-or-one-call contract. Use `AgentRuntime.withModelFactory` to create one adapter per run.

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
mvn "-P!jdk-17" clean test
mvn "-P!jdk-17" exec:java
```

The demo is fully offline and prints `ledger.proof=PASS` when protocol order, call correlation, and tool execution invariants hold.

## Current scope

- Function arguments are currently restricted to required string fields.
- Multiple calls are preflighted together and then executed serially; no thread-pool parallelism is implemented.
- The blocking SDK transport and multi-step Runtime adapter are implemented, but the default demo and test suite use SDK fixtures and never make a live OpenAI request.
- The live adapter deliberately disables parallel tool calls; `OpenAiResponseReplay` retains offline coverage for deterministic multi-call batches.
- No credentialed live compatibility, model-behavior, latency, or cost evidence exists yet.
- The idempotency store is process-local and does not provide crash recovery or distributed exactly-once semantics.
- Authorization and human approval must be added by the application before sensitive handlers execute.

## Architecture decisions

- [Documentation index](docs/README.md)
- [Architecture, class responsibilities, and call paths](docs/architecture.md)
- [Sustainable project mind map](docs/project-mind-map.md)
- [ADR-0001: Runtime and package boundaries](docs/adr/0001-runtime-and-package-boundaries.md)
- [ADR-0002: Responses protocol ledger and call batches](docs/adr/0002-responses-protocol-ledger.md)
- [ADR-0003: Tool contracts and execution authority](docs/adr/0003-tool-contract-and-authority.md)
- [ADR-0004: Retry and idempotency policy](docs/adr/0004-retry-and-idempotency.md)
- [ADR-0005: Response terminal and decoding boundaries](docs/adr/0005-response-terminal-and-decoding.md)
- [ADR-0006: Offline verification boundary](docs/adr/0006-offline-verification-boundary.md)
- [ADR-0007: Stateful Responses continuation](docs/adr/0007-stateful-responses-continuation.md)

## Official references

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Reasoning models](https://developers.openai.com/api/docs/guides/reasoning)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Error codes](https://developers.openai.com/api/docs/guides/error-codes)
- [OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)

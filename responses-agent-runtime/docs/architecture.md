# Architecture, classes, and call paths

[English](architecture.md) | [简体中文](architecture.zh-CN.md)

This document is the current structural map of the codebase. ADRs explain why durable decisions were made; this document explains what exists now and must change with the code.

## System context

```mermaid
flowchart LR
    User[Caller or application] --> Runtime[AgentRuntime]
    Runtime --> Port[AgentModelPort]
    ModelAdapter[OpenAiResponsesModel] -. implements .-> Port
    ModelAdapter --> Transport[OpenAiResponsesTransport]
    Transport --> Responses[OpenAI Responses API]
    Runtime --> Registry[ToolRegistry]
    Registry --> Handler[ToolHandler]
    Handler --> Domain[Application or external systems]

    Fixture[SDK response fixtures] --> Replay[OpenAiResponseReplay]
    Fixture --> ModelAdapter
    Replay --> Registry
    Replay --> Ledger[OpenAiResponseLedger]
    Replay --> Mapper[OpenAI mappers]

    Retry[RetryPolicy] -. composed by transport .-> ModelAdapter
    Idempotency[IdempotentExecutor] -. wraps side effects .-> Handler
```

The blocking SDK transport path is implemented. Default tests replace the network edge with captured requests and SDK response fixtures, so live service compatibility remains unverified. `OpenAiResponseReplay` separately exercises manual heterogeneous-output preservation and deterministic multi-call batches.

## Package dependency direction

```mermaid
flowchart TB
    example[example<br/>offline composition and fixtures]
    openai[openai<br/>Responses SDK adapter]
    runtime[runtime<br/>agent loop]
    tool[tool<br/>validation and dispatch]
    reliability[reliability<br/>retry and idempotency]
    api[api<br/>provider-neutral values]
    internal[internal<br/>shared argument guards]
    sdk[OpenAI Java SDK]
    json[fastjson2]

    example --> runtime
    example --> openai
    example --> tool
    example --> api
    openai --> api
    openai --> tool
    openai --> sdk
    openai --> json
    runtime --> api
    runtime --> tool
    runtime --> internal
    tool --> api
    tool --> internal
    api --> internal
```

`api`, `runtime`, `tool`, and `reliability` must remain free of OpenAI SDK types. `reliability` is intentionally policy-only and is composed at transport or handler boundaries rather than imported by the core loop.

## Provider-neutral runtime classes

```mermaid
classDiagram
    class AgentRuntime {
      -Supplier~AgentModelPort~ modelFactory
      -ToolRegistry tools
      -int maxSteps
      +withModelFactory(modelFactory, tools, maxSteps) AgentRuntime
      +run(String input) Result
    }
    class AgentModelPort {
      <<interface>>
      +decide(AgentContext context) AgentDecision
    }
    class AgentContext {
      +String input
      +List~Exchange~ history
      +List~ToolDefinition~ tools
    }
    class Exchange {
      +ToolCall call
      +ToolResult result
    }
    class AgentDecision {
      <<sealed interface>>
    }
    class FinalAnswer
    class ToolCall
    class AgentStep
    class AgentState {
      <<enumeration>>
      START
      MODEL
      TOOL
      FINAL
      STOP
    }
    class ToolRegistry {
      +definitions() List~ToolDefinition~
      +prepare(ToolCall) PreparedCall
      +prepareAll(List~ToolCall~) List~PreparedCall~
      +execute(PreparedCall) ToolResult
    }
    class ToolHandler {
      <<interface>>
      +execute(Map arguments) String
    }
    class ToolDefinition
    class ToolResult

    AgentRuntime --> AgentModelPort : requests decisions
    AgentRuntime --> ToolRegistry : validates and executes
    AgentRuntime --> AgentStep : records
    AgentRuntime --> AgentState : traces
    AgentModelPort ..> AgentContext
    AgentModelPort ..> AgentDecision
    AgentContext o-- Exchange
    AgentContext o-- ToolDefinition
    Exchange --> ToolCall
    Exchange --> ToolResult
    AgentDecision <|.. FinalAnswer
    AgentDecision <|.. ToolCall
    ToolRegistry o-- ToolDefinition
    ToolRegistry o-- ToolHandler
    ToolRegistry ..> ToolResult
```

### Runtime class catalog

| Class | Function | Important relationships |
| --- | --- | --- |
| [`AgentContext`](../src/main/java/io/github/jamielu/agent/api/AgentContext.java) | Immutable input, successful tool history, and exposed tools for one model decision. | Contains `Exchange`; consumed by `AgentModelPort`. |
| [`AgentDecision`](../src/main/java/io/github/jamielu/agent/api/AgentDecision.java) | Closed model decision: `FinalAnswer` or one provider-neutral `ToolCall`. | Produced by `AgentModelPort`; consumed by `AgentRuntime`. |
| [`AgentState`](../src/main/java/io/github/jamielu/agent/api/AgentState.java) | Observable runtime lifecycle states. | Appended by `AgentRuntime`. |
| [`AgentStep`](../src/main/java/io/github/jamielu/agent/api/AgentStep.java) | One model decision plus an optional matching successful tool observation. | Returned in runtime results. |
| [`ToolDefinition`](../src/main/java/io/github/jamielu/agent/api/ToolDefinition.java) | Provider-neutral tool name, description, and ordered required string parameters. | Registered in `ToolRegistry`; mapped by `OpenAiFunctionToolMapper`. |
| [`ToolResult`](../src/main/java/io/github/jamielu/agent/api/ToolResult.java) | Tool output correlated by the original `callId`. | Stored in history; mapped to `function_call_output`. |
| [`AgentModelPort`](../src/main/java/io/github/jamielu/agent/runtime/AgentModelPort.java) | Boundary for one model decision. | Implemented by `OpenAiResponsesModel` and fixture adapters. |
| [`AgentRuntime`](../src/main/java/io/github/jamielu/agent/runtime/AgentRuntime.java) | Drives the bounded model-tool-model loop and returns typed terminal results. | Obtains one `AgentModelPort` from its factory per run and uses `ToolRegistry`. |
| [`ToolRegistry`](../src/main/java/io/github/jamielu/agent/tool/ToolRegistry.java) | Allowlist, exact argument validation, side-effect-free batch preflight, dispatch, and call/result correlation. | Owns `Registration` and `PreparedCall`; invokes `ToolHandler`. |
| [`ToolHandler`](../src/main/java/io/github/jamielu/agent/tool/ToolHandler.java) | Executes already-validated application logic. | Registered in `ToolRegistry`. |
| [`ToolExecutionException`](../src/main/java/io/github/jamielu/agent/tool/ToolExecutionException.java) | Typed expected handler failure. | Converted to `AgentRuntime.ToolFailed`. |

## OpenAI Responses adapter classes

```mermaid
classDiagram
    class OpenAiResponseReplay {
      +run(firstOutput, secondTurn, registry) ReplayResult
    }
    class OpenAiResponsesModel {
      -String previousResponseId
      -String pendingCallId
      +decide(AgentContext) AgentDecision
    }
    class OpenAiResponsesTransport {
      <<interface>>
      +create(ResponseCreateParams) Response
      +fromClient(OpenAIClient) OpenAiResponsesTransport
    }
    class OpenAiResponsesModelException
    class AgentModelPort
    class OpenAiResponseLedger {
      +prepare(outputItems) PreparedLedger
      +appendAll(prepared, results) List~ResponseInputItem~
    }
    class PreparedLedger
    class OpenAiFunctionCallMapper {
      +mapAll(outputItems) List~ToolCall~
    }
    class OpenAiFunctionCallOutputMapper {
      +map(ToolResult) FunctionCallOutput
    }
    class OpenAiFunctionToolMapper {
      +map(ToolDefinition) FunctionTool
    }
    class ToolRegistry
    class ToolCall
    class ToolResult
    class ResponseOutputItem {
      <<OpenAI SDK>>
    }
    class ResponseInputItem {
      <<OpenAI SDK>>
    }

    AgentModelPort <|.. OpenAiResponsesModel
    OpenAiResponsesModel --> OpenAiResponsesTransport
    OpenAiResponsesModel --> OpenAiFunctionToolMapper
    OpenAiResponsesModel --> OpenAiFunctionCallMapper
    OpenAiResponsesModel --> OpenAiFunctionCallOutputMapper
    OpenAiResponsesModel ..> OpenAiResponsesModelException
    OpenAiResponseReplay --> OpenAiResponseLedger
    OpenAiResponseReplay --> OpenAiFunctionCallMapper
    OpenAiResponseReplay --> ToolRegistry
    OpenAiResponseLedger *-- PreparedLedger
    OpenAiResponseLedger --> OpenAiFunctionCallOutputMapper
    OpenAiResponseLedger ..> ResponseOutputItem
    OpenAiResponseLedger ..> ResponseInputItem
    OpenAiFunctionCallMapper ..> ResponseOutputItem
    OpenAiFunctionCallMapper ..> ToolCall
    OpenAiFunctionCallOutputMapper ..> ToolResult
    OpenAiFunctionCallOutputMapper ..> ResponseInputItem
    OpenAiFunctionToolMapper ..> ToolDefinition
```

### OpenAI adapter class catalog

| Class | Function | Important relationships |
| --- | --- | --- |
| [`OpenAiFunctionToolMapper`](../src/main/java/io/github/jamielu/agent/openai/OpenAiFunctionToolMapper.java) | Maps `ToolDefinition` to strict OpenAI `FunctionTool` schema. | Outbound definition boundary. |
| [`OpenAiFunctionCallMapper`](../src/main/java/io/github/jamielu/agent/openai/OpenAiFunctionCallMapper.java) | Parses all SDK function calls and string-only JSON arguments in response order. | Produces provider-neutral `ToolCall` values. |
| [`OpenAiFunctionCallMappingException`](../src/main/java/io/github/jamielu/agent/openai/OpenAiFunctionCallMappingException.java) | Stable mapping failure categories. | Raised before tool execution. |
| [`OpenAiFunctionCallOutputMapper`](../src/main/java/io/github/jamielu/agent/openai/OpenAiFunctionCallOutputMapper.java) | Maps `ToolResult` to SDK `function_call_output`. | Preserves original `callId`. |
| [`OpenAiResponseLedger`](../src/main/java/io/github/jamielu/agent/openai/OpenAiResponseLedger.java) | Preserves and validates reasoning, assistant message, and function-call protocol history. | Creates `PreparedLedger`; appends mapped results. |
| [`OpenAiResponseLedgerException`](../src/main/java/io/github/jamielu/agent/openai/OpenAiResponseLedgerException.java) | Stable fail-closed protocol errors. | Prevents unsafe continuation. |
| [`OpenAiResponseReplay`](../src/main/java/io/github/jamielu/agent/openai/OpenAiResponseReplay.java) | Orchestrates one offline tool batch and one required final continuation. | Composes ledger, call mapper, and registry. |
| [`OpenAiResponsesTransport`](../src/main/java/io/github/jamielu/agent/openai/OpenAiResponsesTransport.java) | Minimal blocking Responses create boundary with an `OpenAIClient` bridge. | Injected into the live model adapter and replaced by captures in offline tests. |
| [`OpenAiResponsesModel`](../src/main/java/io/github/jamielu/agent/openai/OpenAiResponsesModel.java) | Stateful per-run `AgentModelPort` using `previous_response_id`; repeats configuration and disables parallel calls. | Builds SDK requests through the transport and maps responses to core decisions. |
| [`OpenAiResponsesModelException`](../src/main/java/io/github/jamielu/agent/openai/OpenAiResponsesModelException.java) | Stable failures for invalid IDs, terminal output, and continuation context. | Raised before unsafe continuation or accepting an invalid final answer. |

## Reliability classes

```mermaid
classDiagram
    class FailureKind {
      <<enumeration>>
      +retryable() boolean
    }
    class RetryPolicy {
      +decide(kind, attempts, totalWait, retryAfter) RetryDecision
    }
    class RetryDecision {
      <<sealed interface>>
    }
    class RetryAfter
    class Stop
    class IdempotencyStore~R~ {
      <<interface>>
      +find(operationKey) Optional~Entry~
      +save(operationKey, entry)
    }
    class InMemoryIdempotencyStore~R~
    class IdempotentExecutor~R~ {
      +execute(operationKey, fingerprint, operation) R
    }

    RetryPolicy --> FailureKind
    RetryPolicy --> RetryDecision
    RetryDecision <|.. RetryAfter
    RetryDecision <|.. Stop
    IdempotencyStore <|.. InMemoryIdempotencyStore
    IdempotentExecutor --> IdempotencyStore
```

| Class | Function | Production extension point |
| --- | --- | --- |
| [`FailureKind`](../src/main/java/io/github/jamielu/agent/reliability/FailureKind.java) | Provider-neutral retry classification. | Add a transport-specific error classifier outside this package. |
| [`RetryPolicy`](../src/main/java/io/github/jamielu/agent/reliability/RetryPolicy.java) | Pure bounded exponential backoff, `Retry-After`, jitter, and wait-budget decisions. | A scheduler performs waits and attempts. |
| [`RetryDecision`](../src/main/java/io/github/jamielu/agent/reliability/RetryDecision.java) | Closed `RetryAfter` or `Stop` result. | Transport loop consumes it. |
| [`IdempotencyStore`](../src/main/java/io/github/jamielu/agent/reliability/IdempotencyStore.java) | Storage port for successful operation results and request fingerprints. | Implement with durable transactional storage. |
| [`InMemoryIdempotencyStore`](../src/main/java/io/github/jamielu/agent/reliability/InMemoryIdempotencyStore.java) | Deterministic process-local store. | Replace for concurrency, persistence, or recovery. |
| [`IdempotentExecutor`](../src/main/java/io/github/jamielu/agent/reliability/IdempotentExecutor.java) | Replays the first success and rejects key/fingerprint conflicts. | Wrap side-effecting handlers. |

## Generic Agent Runtime call path

```mermaid
sequenceDiagram
    actor Caller
    participant R as AgentRuntime
    participant M as AgentModelPort
    participant T as ToolRegistry
    participant H as ToolHandler

    Caller->>R: run(input)
    loop until final answer or maxSteps
        R->>M: decide(AgentContext)
        alt FinalAnswer
            M-->>R: FinalAnswer
            R-->>Caller: Completed
        else ToolCall and another model step remains
            M-->>R: ToolCall
            R->>T: execute(call)
            T->>T: prepare(call)
            T->>H: execute(validated arguments)
            H-->>T: output
            T-->>R: ToolResult with original callId
            R->>R: append Exchange and AgentStep
        else rejection, failure, or budget stop
            R-->>Caller: Stopped or ToolFailed
        end
    end
```

## Stateful Responses Runtime path

```mermaid
sequenceDiagram
    actor Caller
    participant R as AgentRuntime
    participant M as OpenAiResponsesModel
    participant X as OpenAiResponsesTransport
    participant O as OpenAI Responses API
    participant T as ToolRegistry

    Caller->>R: run(input)
    R->>R: create one model adapter from factory
    R->>M: decide(empty history)
    M->>X: create(input, model, instructions, tools,<br/>store=true, parallel_tool_calls=false)
    X->>O: responses.create
    O-->>X: response ID + function_call
    X-->>M: SDK Response
    M-->>R: ToolCall(call_id)
    R->>T: validate and execute
    T-->>R: ToolResult(call_id)
    R->>M: decide(history + result)
    M->>X: create(previous_response_id,<br/>function_call_output, repeated configuration)
    X->>O: responses.create
    O-->>X: response ID + final message or next call
    X-->>M: SDK Response
    M-->>R: FinalAnswer or ToolCall
```

Each Runtime run owns one stateful adapter. Context mismatches, unsupported output items, multiple function calls, blank response IDs, and missing final text fail closed. The transport-to-service messages describe the production bridge; default tests stop at the injected transport and make no network call.

## Responses batch continuation path

```mermaid
sequenceDiagram
    participant F as First response fixture
    participant O as OpenAiResponseReplay
    participant L as OpenAiResponseLedger
    participant M as OpenAiFunctionCallMapper
    participant T as ToolRegistry
    participant N as Next-turn fixture

    F->>O: reasoning, C1, C2
    O->>L: prepare(all output items)
    L-->>O: PreparedLedger
    O->>M: mapAll(output items)
    M-->>O: ToolCall 1, ToolCall 2
    O->>T: prepareAll(calls)
    T-->>O: PreparedCall 1, PreparedCall 2
    O->>T: execute(PreparedCall 1)
    T-->>O: O1
    O->>T: execute(PreparedCall 2)
    T-->>O: O2
    O->>L: appendAll(prepared, O1, O2)
    L-->>O: reasoning, C1, C2, O1, O2
    O->>N: continuation input
    N-->>O: final reasoning or message items
```

No handler can run before protocol validation, mapping, and complete batch preflight succeed. Execution remains serial and non-transactional.

## Change-impact map

| Change | Primary code | Required documentation and tests |
| --- | --- | --- |
| Add an API value or decision type | `api`, then `runtime` consumers | Core class diagram, class catalog, runtime tests, and a new ADR if the abstraction changes. |
| Add a provider | New adapter package | Package graph, adapter diagram, contract tests; keep core packages provider-neutral. |
| Add a tool argument type | `ToolDefinition`, registry validation, provider mappers | Tool ADR, schema tests, invalid-input tests, and both READMEs. |
| Evolve the live OpenAI transport | `OpenAiResponsesTransport` and `OpenAiResponsesModel` | Stateful sequence, ADR-0007, retry/error-classification tests, credential and cost guidance. |
| Add parallel tool execution | New execution policy around `PreparedCall` | Ordering/failure ADR, sequence diagram, race and partial-failure tests. |
| Add durable idempotency | `IdempotencyStore` implementation | Deployment architecture, transaction semantics ADR, crash/concurrency tests. |
| Add streaming or structured final output | OpenAI transport/decoder layer | ADR-0005 successor or extension, terminal-state diagrams, incomplete/refusal tests. |

## Source of truth and update rule

- Java source and executable tests define behavior.
- This architecture document describes the current shape and changes with the code.
- ADRs preserve the reason for durable decisions and are superseded rather than rewritten.
- Mermaid identifiers should remain stable where possible so future diffs show architectural change rather than diagram churn.
- Every structural code review should check the package graph, affected class catalog row, affected sequence, and the corresponding Chinese document.

## Official references

- [OpenAI Agents SDK vs. Responses API](https://developers.openai.com/api/docs/guides/agents#agents-sdk-vs-responses-api)
- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Reasoning models](https://developers.openai.com/api/docs/guides/reasoning)

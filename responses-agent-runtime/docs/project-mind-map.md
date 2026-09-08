# Sustainable project mind map

[English](project-mind-map.md) | [简体中文](project-mind-map.zh-CN.md)

This map separates implemented capabilities from explicit extension points so the architecture can evolve without overstating readiness.

## Capability map

```mermaid
mindmap
  root((Responses Agent Runtime))
    Provider-neutral core
      API values
        AgentContext
        AgentDecision
        AgentStep and AgentState
        ToolDefinition and ToolResult
      Runtime
        Bounded model-tool loop
        Per-run model factory
        Typed terminal results
        Duplicate call protection
        Step budget
    Tool boundary
      Allowlisted registry
      Exact argument validation
      Side-effect-free batch preflight
      Serial deterministic execution
      Typed expected failures
    OpenAI Responses adapter
      Strict FunctionTool mapping
      Heterogeneous output traversal
      Multiple function calls
      Reasoning and message preservation
      Exact call_id correlation
      Offline two-turn replay
      Blocking SDK transport bridge
      Stateful previous_response_id continuation
      Repeated instructions and tools
      store enabled
      Parallel tool calls disabled
    Reliability policy
      Failure classification
      Retry-After minimum
      Exponential backoff and jitter
      Attempt and wait budgets
      Operation-key idempotency
      In-memory proof store
    Verification
      Unit tests
      Cross-package contract tests
      Offline SDK fixtures
      Executable ledger proof
      Javadoc
    Next production slice
      Credentialed live verification
      OpenAI error classification
      Unified SDK and app retry budget
      Credential and cost controls
      Tagged live integration tests
    Later hardening
      Rich JSON Schema types
      Durable idempotency
      Authorization and approval
      Streaming and structured final output
      Observability and evaluations
      Parallel execution policy
```

## Iteration path and quality gates

```mermaid
flowchart LR
    A[Current baseline<br/>SDK transport adapter plus offline contract proof] --> B[Opt-in live verification<br/>credentials, errors, retry, and cost]
    B --> C[Production safety<br/>auth, approval, durable idempotency]
    C --> D[Response modes<br/>streaming and structured output]
    D --> E[Operations<br/>tracing, metrics, evaluations]
    E --> F[Scale policies<br/>parallel tools and additional providers]

    A --- GA[Gate<br/>unit and contract tests]
    B --- GB[Gate<br/>tagged live tests and cost limits]
    C --- GC[Gate<br/>failure, crash, and authorization tests]
    D --- GD[Gate<br/>terminal, refusal, and incomplete tests]
    E --- GE[Gate<br/>trace coverage and eval baselines]
    F --- GF[Gate<br/>ordering, race, and load tests]
```

## Iteration contract

Every capability moves through the same lifecycle:

1. **Contract:** define provider-neutral behavior and failure states before selecting SDK shapes.
2. **Decision:** add or supersede an ADR when dependency direction, ownership, or irreversible semantics change.
3. **Implementation:** keep provider-specific types inside adapters and preflight before side effects.
4. **Proof:** add deterministic unit and contract tests; add live tests only behind explicit credentials and cost controls.
5. **Documentation:** update both languages, the architecture class catalog, the affected call path, and this capability map.
6. **Release gate:** report what the tests prove and what remains unverified.

## Definition of done by change type

| Change type | Minimum proof | Documentation impact |
| --- | --- | --- |
| Core API or runtime behavior | Unit tests, terminal/failure cases, immutable snapshots | Class diagram, class catalog, relevant ADR. |
| OpenAI protocol mapping | SDK fixture tests, unknown-item tests, exact ordering and `call_id` assertions | Responses sequence and protocol ADR. |
| Tool execution behavior | Preflight-with-zero-side-effect tests and execution-failure tests | Tool ADR and failure path. |
| Retry or idempotency | Pure policy boundary tests, conflict/failure cases | Reliability diagram and ADR. |
| Live integration | Explicit opt-in, credential isolation, bounded requests and costs | README scope, system context, verification boundary. |
| Parallel or distributed behavior | Race, ordering, partial failure, crash, and recovery tests | New ADR plus deployment/runtime diagrams. |

## Maintenance checklist

- Keep English and `.zh-CN.md` documents semantically equivalent.
- Treat the source tree and tests as behavior truth; treat diagrams as navigational models.
- Link class catalog rows to source files rather than copying implementation details.
- Mark future capabilities as future until executable evidence exists.
- Prefer adding a focused diagram or ADR over enlarging one diagram until it becomes unreadable.
- Review this map whenever a milestone changes from “next” to “implemented.”

## Related documents

- [Architecture, classes, and call paths](architecture.md)
- [Documentation index](README.md)
- [ADR directory](adr/0001-runtime-and-package-boundaries.md)

# ADR-0007: Stateful Responses continuation with `previous_response_id`

[English](0007-stateful-responses-continuation.md) | [简体中文](0007-stateful-responses-continuation.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

`AgentRuntime` consumes a provider-neutral `AgentModelPort` whose decision is either one tool call or a final answer. The existing `OpenAiResponseReplay` proves manual preservation of heterogeneous Responses output, including multi-call batches, but it does not send requests through an OpenAI SDK client or support an arbitrary number of runtime steps.

A production bridge must retain the Responses conversation across tool turns without leaking SDK types into the core. It must also prevent one stateful provider session from being shared accidentally by separate or concurrent runtime runs.

## Decision

1. `OpenAiResponsesTransport` is the minimal blocking `responses.create` boundary. `fromClient(OpenAIClient)` is the production SDK bridge, while tests inject an in-memory transport.
2. `OpenAiResponsesModel` implements `AgentModelPort` and owns one run's provider state.
3. The initial request sends the user's text. A continuation sends the latest tool result as one `function_call_output` with its original `call_id` and sets `previous_response_id` to the immediately preceding response ID.
4. Every request repeats the model, optional instructions, and function tools. It explicitly sets `store=true` and `parallel_tool_calls=false`.
5. `parallel_tool_calls=false` intentionally narrows each provider turn to zero or one function call, matching the current core port. The separate replay adapter continues to prove deterministic multi-call batch semantics.
6. `AgentRuntime.withModelFactory` creates a fresh model adapter for every `run`. Sharing one `OpenAiResponsesModel` across runs is outside the contract.
7. The adapter fails closed for blank response IDs, multiple or unsupported tool output items, unsupported terminal items, missing final text, and continuation context mismatches.
8. Default verification remains offline. SDK response fixtures and a captured transport prove request construction and runtime wiring without credentials, network access, or billing.

## Consequences

- The generic runtime can now drive a blocking Responses API session for as many bounded steps as required.
- Core `api`, `runtime`, `tool`, and `reliability` packages remain independent of OpenAI SDK types.
- Continuation depends on provider-side stored response state. The explicit `store=true` choice is not appropriate for a zero-data-retention deployment; such a deployment needs a separate manual replay or encrypted-reasoning design.
- Instructions and tools are deliberately rebuilt on each continuation rather than assumed to carry over.
- The transport boundary is ready for retry, error classification, tracing, and opt-in live integration tests, but none of those capabilities is implied by this ADR.
- Passing offline tests proves SDK object construction and local state transitions, not live service compatibility, account access, latency, or model behavior.

## References

- [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [OpenAI Responses API reference](https://developers.openai.com/api/reference/resources/responses/methods/create)


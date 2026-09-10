# Responses Agent Runtime Recall Questions (with Answers)

[English](project-recall-qa.md) | [简体中文](project-recall-qa.zh-CN.md)

This document supports active recall after project completion and introduces no new learning scope. Hide each answer, respond from memory, and then verify against the code, tests, and official documentation.

Sources of truth: the current repository and [OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling).

## 1. Goals and architecture boundaries

### 1. What core problem does this project solve?

Answer: It provides a bounded Java 21 Agent Runtime that combines model decisions, tool validation and execution, Responses protocol adaptation, budgets, and explicit failure terminals in a runnable and testable project.

### 2. Why must the core Runtime remain provider-neutral?

Answer: The loop then depends only on local ports and values, remains insulated from OpenAI SDK changes, supports alternative providers, and stays fully offline-testable.

### 3. What does each of `api`, `runtime`, `tool`, `openai`, `online`, and `reliability` own?

Answer: `api` owns neutral values, `runtime` the loop and budgets, `tool` the allowlist and execution, `openai` Responses adaptation, `online` configuration and production composition, and `reliability` retry and idempotency policy.

### 4. What is the single responsibility of `AgentRuntime`?

Answer: It drives the bounded model–tool–model lifecycle and normalizes every exit into an explicit terminal result.

### 5. Why is `AgentModelPort` intentionally narrow?

Answer: Each Runtime step only needs one `AgentDecision` for an `AgentContext`; a narrow port prevents SDK requests, connections, and protocol details from leaking into the loop.

### 6. What role does `OpenAiResponsesModel` play?

Answer: It is the OpenAI `AgentModelPort` adapter coordinating conversation requests, transport, and decoding without executing application tools.

### 7. Why does `OpenAiResponsesTransport` exist separately?

Answer: It confines blocking `responses.create` calls to an injectable boundary, lets tests capture requests without networking, and centralizes SDK exception mapping.

### 8. What state does `OpenAiConversation` retain?

Answer: The initial input and tool snapshot, previous response ID, pending `call_id`, and history size at the previous decision.

### 9. Why can `OpenAiResponseDecoder` not read a fixed array position?

Answer: Responses `output` is heterogeneous and may contain reasoning, message, or function-call items, so a fixed index can misread a valid response.

### 10. What does ArchUnit protect?

Answer: Core packages may not depend on OpenAI, online, offline, or example packages, and the OpenAI adapter may not depend back on composition entry points.

## 2. Runtime, budgets, and terminals

### 11. What are the main Runtime trace states?

Answer: `START`, `MODEL`, `TOOL`, `FINAL`, and `STOP`.

### 12. What four terminal result types exist?

Answer: `Completed`, `Stopped`, `ModelFailed`, and `ToolFailed`.

### 13. Why are terminal results a sealed interface?

Answer: The result set is closed, allowing callers to exhaustively handle success, deliberate stops, model failure, and tool failure.

### 14. Which resources does `RunBudget` currently limit?

Answer: Cross-turn model calls, application tool calls, and total monotonic run duration.

### 15. Why must the model-call budget be checked before a request?

Answer: A sent request may already create cost or external state; a post-request check cannot prevent overspending.

### 16. Why must the tool budget be checked before handler side effects?

Answer: The budget must prevent additional side effects, not merely report that the limit was exceeded afterward.

### 17. Why does the deadline use a monotonic clock?

Answer: Monotonic time is unaffected by wall-clock corrections or time-zone changes and is appropriate for elapsed duration.

### 18. Why is `RunUsage` present on every terminal result?

Answer: Callers can audit model calls, tool calls, and elapsed time for success, stops, and failures alike.

### 19. Why does each `run` obtain a separate model instance from a factory?

Answer: `OpenAiResponsesModel` stores continuation state; sharing it would leak response IDs and pending calls across runs.

### 20. Why does Runtime detect repeated `call_id` values?

Answer: Re-executing the same call within one run could duplicate side effects, so Runtime fails closed before execution.

## 3. Tool contracts, protocol ledger, and reliability

### 21. Why is `ToolRegistry` also an allowlist?

Answer: A model can request a tool but cannot grant execution authority; only locally registered tools and handlers may execute.

### 22. What is side-effect-free preflight?

Answer: Tool existence, exact argument names, required values, and call correlation are validated before any handler runs.

### 23. Why preflight an entire multi-call batch before executing its first call?

Answer: If a later call is invalid, early execution would create avoidable partial side effects.

### 24. What parameter boundary does `ToolDefinition` currently support?

Answer: Ordered required string fields only; complex types remain an explicit extension boundary.

### 25. Why does the OpenAI tool schema use strict mode?

Answer: Strict mode constrains generated arguments to JSON Schema; the project also requires every property and rejects additional properties.

### 26. What is the actual purpose of `call_id`?

Answer: It correlates a `function_call_output` with a specific model function call; it is a protocol correlation key, not a business idempotency key.

### 27. What problem does `OpenAiResponseLedger` solve?

Answer: It validates and preserves reasoning, message, and function-call items, then appends tool results with correct correlation.

### 28. Why preserve the original protocol-item order?

Answer: Order is part of model context; changing it can alter continuation semantics or break call/output relationships.

### 29. Why must the first handler remain unexecuted if the second call fails preflight?

Answer: The complete batch has not passed safety validation, and running the first call could create an unnecessary irreversible side effect.

### 30. Is the first call rolled back if the second call fails during execution?

Answer: No. The project stops later calls but does not promise rollback of completed external side effects; compensation belongs to the application.

### 31. What are the separate roles of an idempotency operation key and request fingerprint?

Answer: The key identifies one business operation; the fingerprint proves the replayed request is identical. Same key with a different fingerprint is rejected.

### 32. Why is `RetryPolicy` a pure decision instead of calling `sleep`?

Answer: Pure policy is deterministic to test and leaves scheduling, threading, and cancellation to the actual transport or executor.

## 4. OpenAI Responses and function calling

### 33. How do a tool, tool call, and tool call output differ?

Answer: A tool is an application-declared capability, a tool call is a model request to use it, and a tool call output is the application result returned to the model.

### 34. What are the five high-level function-calling steps?

Answer: Send a request with tools, receive a tool call, execute application code, send the output back, and receive a final answer or more calls.

### 35. Why must Responses `output` be traversed by type?

Answer: It can contain reasoning, message, `function_call`, and other item types; text is not guaranteed at a fixed index.

### 36. How many function calls may one model response contain?

Answer: Applications should handle zero, one, or multiple calls rather than assume a single call.

### 37. What does `parallel_tool_calls=false` guarantee?

Answer: It constrains a response to zero or one tool call, matching this project's single-decision online Runtime port.

### 38. What two key object-schema rules does strict mode require?

Answer: Set `additionalProperties=false` and include every property in `required`.

### 39. How is an optional field represented in strict mode?

Answer: Keep it required but allow `null` in its type.

### 40. Do function definitions consume tokens?

Answer: Yes. OpenAI Docs states that definitions enter model context and count against context limits and billed input tokens.

### 41. What should happen to reasoning items returned with tool calls?

Answer: For reasoning models, OpenAI Docs says they should be passed back together with tool-call outputs; the project ledger preserves them.

### 42. What format does `function_call_output` usually use?

Answer: Usually a string containing JSON, an error code, or plain text; image or file objects are also available for those cases.

### 43. What common `tool_choice` modes exist?

Answer: `auto`, `required`, a forced function, `allowed_tools`, and `none`.

### 44. How does this project use `previous_response_id`?

Answer: It stores the first response ID and sends it with the matching `function_call_output` after successful tool execution.

### 45. Why does the project reapply model, instructions, and tools on every turn?

Answer: This keeps request configuration explicit, avoids hidden client state, and lets request-capture tests verify the complete invariant.

## 5. Errors, testing, and release

### 46. Why map OpenAI SDK exceptions to `FailureKind`?

Answer: Core Runtime should not depend on provider exception classes; stable categories keep retry, exit-code, logging, and caller behavior consistent.

### 47. Why may the SDK and application not own independent retry loops simultaneously?

Answer: Layered retries multiply attempts, cost, and latency and make the global budget inaccurate.

### 48. How does default `clean verify` avoid API cost?

Answer: Default tests use fakes, fixtures, or injectable transports, do not explicitly run the online CLI, and do not execute `*IT` through Failsafe.

### 49. Where do production code, test code, and profiles now belong?

Answer: All production code is under `src/main/java`, all tests and fixtures under `src/test/java`, and profiles only select offline/online entry points or enable the live IT.

### 50. What are the most important release evidence and residual risks?

Answer: Evidence includes default build, 100% line/branch gates, Javadoc, offline demo, CLI exit behavior, and documentation checks. Residual risks include process-local idempotency, no side-effect rollback, application-owned authorization/approval, and limited live latency, cost, and model-behavior evidence.

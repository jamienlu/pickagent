# ADR-0002: Responses protocol ledger and call batches

[English](0002-responses-protocol-ledger.md) | [简体中文](0002-responses-protocol-ledger.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

Responses output is an ordered heterogeneous array. A tool turn can include reasoning items, assistant messages, and zero, one, or multiple function calls. A `call_id` correlates a call with its output but does not preserve the remaining protocol history.

Reasoning models need relevant reasoning items returned with tool outputs. Rebuilding those items from visible fields risks losing opaque encrypted content, status, phase, or future SDK fields.

## Decision

For a tool turn:

1. Snapshot and validate every output item before running any handler.
2. Preserve supported reasoning, assistant-message, and function-call SDK objects in their original order.
3. Map every function call in response order and reject blank or duplicate `call_id` values.
4. Preflight the complete tool batch before its first side effect.
5. Execute prepared calls serially in response order.
6. Append one `function_call_output` for every call, in the same order and with the exact original `call_id`.
7. Invoke the next model turn only after the whole batch succeeds.
8. Treat unexpected protocol items as fail-closed until explicitly supported.

For `reasoning, C1, C2`, the next input is `reasoning, C1, C2, O1, O2`.

## Consequences

- An invalid later call cannot cause an earlier handler to execute.
- An execution failure stops later calls and suppresses the next model request.
- Successful side effects completed before a later execution failure are not rolled back.
- Batch execution is deterministic but not transactional.
- Parallel execution remains a separate future policy; accepting multiple model calls does not require local concurrency.

## References

- [OpenAI Function calling: Handling function calls](https://developers.openai.com/api/docs/guides/function-calling#handling-function-calls)
- [OpenAI Reasoning models: Keeping reasoning items in context](https://developers.openai.com/api/docs/guides/reasoning#keeping-reasoning-items-in-context)

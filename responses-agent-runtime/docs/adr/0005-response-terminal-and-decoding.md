# ADR-0005: Response terminal and decoding boundaries

[English](0005-response-terminal-and-decoding.md) | [简体中文](0005-response-terminal-and-decoding.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

Earlier exercises covered token budgeting, streaming, and structured output. They are not copied as separate daily modules, but their failure boundaries remain relevant when the runtime later adds live Responses transport and structured final answers.

## Decision

- Treat character-count heuristics as estimates, not tokenization. Reserve output budget explicitly and use subtraction-based overflow-safe checks.
- Traverse heterogeneous `output` items by type; never assume text at a fixed index.
- For streaming, `response.output_text.done` completes one text part, while `response.completed` is the response-level success terminal.
- Do not append the full text from a done event to accumulated deltas; compare it exactly as an integrity check.
- Track text buffers by item and content indexes when multiple parts are supported.
- Treat refusal, incomplete response, provider failure, transport failure, and invalid application output as distinct outcomes.
- Only a completed output-text item may enter structured decoding.
- Structured Outputs provides schema adherence for supported schemas; JSON mode guarantees only valid JSON.
- Validate the exact allowed field set and business rules before constructing domain objects. Refusal and incomplete output bypass JSON decoding.

## Consequences

- Partial or refused output cannot accidentally become a successful business command.
- Stream handling remains transport-independent and offline-testable.
- The current project records these constraints for the future live adapter without retaining obsolete daily DTOs and demos.

## References

- [OpenAI Responses API reference](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)

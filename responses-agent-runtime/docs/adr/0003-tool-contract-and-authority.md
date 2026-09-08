# ADR-0003: Tool contracts and execution authority

[English](0003-tool-contract-and-authority.md) | [简体中文](0003-tool-contract-and-authority.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

A model-generated call is untrusted input. Strict function schemas improve argument-shape reliability but do not prove that a user may perform an operation or that the operation is valid in the current business state.

## Decision

Enforce distinct boundaries:

1. The OpenAI mapper validates provider protocol fields and parses argument JSON.
2. `ToolRegistry` allows only registered names and requires the runtime argument set to match the declared contract exactly.
3. `ToolRegistry.prepareAll` validates the entire batch without invoking handlers.
4. Application authorization, tenant checks, approval, limits, and business-state validation must run before a sensitive side effect.
5. A handler cannot choose or rewrite `call_id`; the registry copies it from the validated call into the result.
6. Known tool failures use a typed checked exception. Unknown programming failures propagate instead of being mislabeled as transport errors.

Strict tool schemas use `strict: true`, require every property, and set `additionalProperties: false` for each object. Optional values must be modeled explicitly rather than silently omitted.

## Consequences

- Schema adherence is not confused with authorization.
- Models cannot select arbitrary Java methods or classes.
- Missing, extra, and blank values are rejected before handlers run.
- Security policy remains application-owned and can evolve independently of OpenAI schema mapping.

## Reference

- [OpenAI Function calling: Strict mode](https://developers.openai.com/api/docs/guides/function-calling#strict-mode)

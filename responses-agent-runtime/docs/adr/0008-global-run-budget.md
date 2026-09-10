# ADR-0008: Global run budget

[English](0008-global-run-budget.md) | [简体中文](0008-global-run-budget.zh-CN.md)

- Status: Accepted
- Date: 2026-09-09

## Context

Per-response output limits and SDK request timeouts do not bound a multi-turn model/tool loop. A looping model can consume additional calls, execute more side effects, or exceed an application deadline even when each individual request is valid.

## Decision

Every `AgentRuntime` run owns an immutable `RunBudget` with maximum model calls, maximum application tool calls, and an optional monotonic deadline. The Runtime checks budget before a model request and again before a tool side effect. It returns stable stop reasons instead of throwing for expected exhaustion.

All normal terminal results—completed, stopped, model failed, and tool failed—carry an immutable `RunUsage` snapshot. Deadline behavior uses injected `NanoClock` so tests are deterministic and independent of wall-clock changes. The legacy maximum-step constructor maps to the new budget for compatibility.

## Consequences

- Call and side-effect ceilings apply across the whole run, not one response.
- Operators can distinguish model-call, tool-call, and deadline stops.
- Usage counts logical Runtime calls; SDK-internal retry attempts and provider Token usage are not included.
- Token and monetary budgets remain future extensions and require an explicit provider-neutral usage contract.

## Verification

Unit tests cover validation, exact boundary behavior, deadlines, every terminal snapshot, duplicate calls, failures, and immutable result collections. JaCoCo enforces full line and branch coverage.

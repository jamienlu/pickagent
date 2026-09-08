# ADR-0001: Runtime and package boundaries

[English](0001-runtime-and-package-boundaries.md) | [简体中文](0001-runtime-and-package-boundaries.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

The source project grew through daily exercises. Runtime policy, provider SDK mapping, tools, fixtures, and learning records were grouped by week rather than by responsibility. That structure made the dependency direction and the reusable runtime hard to see.

## Decision

Use stable capability packages:

- `io.github.jamielu.agent.api` contains provider-neutral immutable structures.
- `io.github.jamielu.agent.runtime` owns the Agent loop, lifecycle, history, and stop budgets.
- `io.github.jamielu.agent.tool` owns tool allowlisting, validation, preflight, and dispatch.
- `io.github.jamielu.agent.openai` owns OpenAI SDK mapping and Responses protocol state.
- `io.github.jamielu.agent.reliability` owns retry and idempotency policies.
- `io.github.jamielu.agent.example` contains reproducible adapters and the executable demonstration.

The model port is defined next to the runtime that consumes it. Provider adapters implement or feed that port. Runtime and API code must not import OpenAI SDK classes.

## Consequences

- SDK upgrades are localized to the OpenAI adapter.
- Runtime tests construct small provider-neutral values.
- Provider-specific protocol state is not falsely flattened into the generic Agent history.
- Package names describe long-lived responsibilities instead of a learning date.
- Adding another provider does not require changing the API records or tool handlers unless a genuinely new core capability is required.

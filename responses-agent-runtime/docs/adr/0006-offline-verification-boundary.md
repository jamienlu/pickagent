# ADR-0006: Offline verification boundary

[English](0006-offline-verification-boundary.md) | [简体中文](0006-offline-verification-boundary.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

Default tests must be fast, deterministic, free of credentials, and safe to run repeatedly. SDK fixtures can verify adapter behavior but cannot establish properties of the live OpenAI service.

## Decision

Use offline tests as the default quality gate. They verify:

- API value invariants and immutable snapshots.
- Agent lifecycle, step budgets, duplicate-call stopping, and typed failures.
- Strict schema mapping at the SDK boundary.
- Protocol parsing, `call_id` round trips, and reasoning-item preservation.
- Complete batch preflight, ordered serial execution, and failure stopping.
- Retry delay calculations and local idempotency rules.

Do not claim that these tests prove:

- Live model quality, availability, latency, billing, quota, or schema adherence rates.
- Proxy, TLS, timeout, or streaming behavior over a real network.
- SDK automatic retry interaction beyond the installed-version code path.
- Distributed idempotency, crash recovery, or exactly-once side effects.
- Application authorization or approval policy.

Live integration tests must be separately tagged, explicitly enabled, and given their own credentials and cost controls when added.

## Consequences

- `mvn test` is reproducible and needs no OpenAI API key.
- The executable demo is an offline protocol proof, not evidence of live service compatibility.
- Production readiness requires a distinct integration and evaluation layer.

# ADR-0004: Retry and idempotency policy

[English](0004-retry-and-idempotency.md) | [简体中文](0004-retry-and-idempotency.zh-CN.md)

- Status: Accepted
- Date: 2026-09-08

## Context

HTTP status alone does not determine whether an OpenAI request is retryable. A 429 temporary rate limit and a 429 credit or spend-limit error require different actions. Nested SDK and application retries can multiply the real request count. Retrying a side effect without a stable business identifier can duplicate the operation.

## Decision

- Map provider errors to stable failure kinds before consulting retry policy.
- Retry only explicitly transient failures such as temporary rate limiting, overload, or selected transport timeouts.
- Do not retry authentication, invalid-request, billing, credit, spend-limit, or usage-limit failures unchanged.
- Treat a valid `Retry-After` value as a minimum and add injected jitter.
- Without a valid server delay, use capped exponential backoff plus jitter.
- Bound both total attempts and cumulative waiting time.
- Keep retry calculation pure; scheduling and sleeping belong to the caller.
- Disable SDK retries or account for them in the same global budget.
- Use a business `operationKey` and request fingerprint for side-effect idempotency. `call_id` is only protocol correlation.
- Cache successful idempotent results. Do not permanently cache transient failures. Reject reuse of one operation key with a different fingerprint.

## Consequences

- Retry behavior can be tested without clocks or sleeps.
- Temporary failure does not become a permanent poisoned result.
- The in-memory implementation proves only single-process semantics.
- Production writes still need durable storage, uniqueness constraints, and a transaction strategy for the gap between external commit and idempotency-record persistence.

## References

- [OpenAI Error codes](https://developers.openai.com/api/docs/guides/error-codes)
- [OpenAI Rate limits](https://developers.openai.com/api/docs/guides/rate-limits)

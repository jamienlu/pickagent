# ADR-0009: Profile isolation and responsibility split

[English](0009-profile-isolation-and-responsibility-split.md) | [简体中文](0009-profile-isolation-and-responsibility-split.zh-CN.md)

- Status: Accepted
- Date: 2026-09-09
- Supersedes: the source-placement portion of ADR-0006 and the monolithic adapter shape described by ADR-0007

## Context

Production code, deterministic fixtures, an online CLI, and credentialed smoke tests have different trust and execution boundaries. Keeping them in one default source set risks accidental network access and ships test fixtures. The stateful OpenAI adapter also owned request construction, conversation state, transport, decoding, error mapping, and online resource lifecycle, making changes difficult to isolate.

## Decision

Use Maven profiles to separate source sets:

- default: production libraries and deterministic unit/contract tests;
- `offline`: deterministic demo and fixture support;
- `online`: explicit CLI entry point;
- `live`: credentialed integration test only.

Split responsibilities as follows:

- `OpenAiResponsesModel` coordinates the provider port;
- `OpenAiConversation` owns request construction and continuation state;
- `OpenAiResponseDecoder` interprets SDK output;
- `OpenAiResponsesTransport` is the narrow SDK call boundary;
- `OpenAiExceptionMapper` produces stable provider-neutral failures;
- `OpenAiOnlineConfig` validates external configuration;
- `OpenAiClientFactory` creates the one retry-owning SDK client;
- `OpenAiOnlineRunner` owns per-run client lifecycle and composition.

ArchUnit enforces that provider-neutral core packages do not depend on OpenAI or online composition. The default build must require no key, network, or paid request. A live request requires both explicit profile activation and explicit credentials.

## Consequences

- Default CI is deterministic and safe to run repeatedly.
- Production objects have focused reasons to change and can be tested through narrow injection points.
- Profile-specific classes are not available unless their source set is selected.
- Profile compilation must be part of release verification to prevent dormant entry-point drift.
- Live service compatibility, latency, cost, and model behavior remain separately approved evidence.

## Verification

The default test suite includes lifecycle, configuration, transport, decoding, mapping, package-boundary, and profile-independent behavior tests. Dedicated offline and online test source sets bring their profile-only entry points under the same coverage gate. Release checks fully verify `offline` and `online`, compile `live`, and never execute a live request without explicit approval.

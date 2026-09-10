# Testing

[English](testing.md) | [简体中文](testing.zh-CN.md)

## Test layers

- Unit tests cover values, guards, budgets, retry decisions, idempotency, mappers, decoding, configuration, and resource lifecycle.
- Contract tests compose packages and SDK fixture objects without a network.
- ArchUnit tests enforce dependency direction so core packages cannot import OpenAI or online-entry types.
- The offline demo provides a deterministic executable acceptance proof.
- The live smoke test is optional, credentialed, and excluded from ordinary verification.

## Required gate

```powershell
mvn "-P!jdk-17" clean verify
```

The gate runs 171 default tests, enforces 100% line and 100% branch coverage for project production classes, and generates Javadoc with `failOnWarnings=true`. The unit-test fork has a 120-second hard timeout so a stalled test cannot hold the build indefinitely. Every `@Test` method has a Chinese comment describing scenario, behavior, and expectation. Tests must assert behavior; broad JaCoCo exclusions and output-only smoke tests are not acceptable substitutes.

## Additional checks

```powershell
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
```

The default profile runs 171 tests. The offline profile runs 172 tests, including its deterministic demo acceptance test. The online profile runs 179 tests, including eight CLI tests that exercise terminal-to-exit-code mapping without credentials or network access. All three non-live profile gates enforce 100% line and branch coverage for the production classes they compile.

Expected offline evidence includes `ledger.proof=PASS`, two model calls, and one tool call. Online verification and live compilation must not send a request. A direct `mvn "-P!jdk-17" javadoc:javadoc` remains available for documentation-only iteration, but release verification does not need to run it twice.

## Adding tests

Place provider-neutral behavior tests beside their package. Use captured `ResponseCreateParams` and SDK response fixtures for OpenAI contract behavior. Inject `NanoClock` for deadline tests and factories for state/resource isolation. Verify terminal type, trace, history, steps, usage, side-effect count, exact call ID correlation, and failure category as applicable.

The live test must never become the only proof of a behavior. It establishes endpoint compatibility, not deterministic correctness.

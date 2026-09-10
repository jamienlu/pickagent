# ADR-0010: Standard Maven source layout

[English](0010-standard-maven-source-layout.md) | [简体中文](0010-standard-maven-source-layout.zh-CN.md)

- Status: accepted
- Date: 2026-09-10
- Supersedes: the custom-source-directory part of ADR-0009

## Context

ADR-0009 used custom Maven source directories to keep the offline demo, online CLI, and live smoke test behind profiles. That arrangement made the execution boundary visible but violated the project's desired conventional layout: production code should live only under `src/main/java`, and tests or test fixtures should live only under `src/test/java`.

Network safety does not require unconventional source locations. It depends on whether deterministic tests instantiate a live client or explicitly execute a credentialed entry point.

## Decision

- Move `OfflineResponsesDemo` and `OpenAiAgentCli` to `src/main/java`.
- Move their tests and `OpenAiLiveSmokeIT` to `src/test/java`.
- Remove every `build-helper-maven-plugin` source-directory addition.
- Keep the `offline` and `online` profiles only to select an `exec:java` main class.
- Keep the `live` profile only to enable Failsafe execution of the `*IT` test. Ordinary Surefire execution does not include `*IT`.
- Compile and test all production entry points in the default deterministic gate. No default test may create a real OpenAI client or send a request.

## Consequences

- IDEs, Maven, static analysis, Javadoc, JaCoCo, and packaging see a conventional project without profile-specific source discovery.
- The production JAR contains both offline and online entry points because both are production classes.
- The default test gate covers the offline demo and online CLI behavior without credentials or network access.
- Execution profiles remain an operator control, but they are no longer a code-location boundary.
- A live request still requires explicit profile activation plus explicit credentials and model configuration.

## Verification

Repository checks reject production `.java` files outside `src/main/java` and test `.java` files outside `src/test/java`. Default `clean verify` must pass without `OPENAI_API_KEY`, network access, or paid requests. The offline profile must still print `ledger.proof=PASS`, and the live test must compile while remaining excluded from ordinary Surefire execution.

# Documentation

[English](README.md) | [简体中文](README.zh-CN.md)

This directory is the long-lived knowledge base for Responses Agent Runtime. Every maintained document has an English source and a Simplified Chinese companion with the same basename plus `.zh-CN`.

## Start here

- [Project README](../README.md): capabilities, package boundaries, build, and current scope.
- [Architecture](architecture.md): system context, package and class relationships, runtime sequences, and change-impact guidance.
- [Project mind map](project-mind-map.md): current capabilities, constraints, and sustainable iteration roadmap.
- [Configuration](configuration.md): environment variables, defaults, standard source layout, execution profiles, and retry ownership.
- [Operations](operations.md): deployment controls, observability, rollout, rollback, and incident evidence.
- [Troubleshooting](troubleshooting.md): stable failure categories and corrective actions.
- [Testing](testing.md): test layers, coverage policy, commands, and contribution rules.
- [Release verification](release-verification.md): deterministic release checklist and residual-risk sign-off.
- [Project and OpenAI recall questions](project-recall-qa.md): fifty review questions with concise answers grounded in this project and OpenAI Docs.

## Architecture decision records

- [ADR-0001: Runtime and package boundaries](adr/0001-runtime-and-package-boundaries.md)
- [ADR-0002: Responses protocol ledger and call batches](adr/0002-responses-protocol-ledger.md)
- [ADR-0003: Tool contracts and execution authority](adr/0003-tool-contract-and-authority.md)
- [ADR-0004: Retry and idempotency policy](adr/0004-retry-and-idempotency.md)
- [ADR-0005: Response terminal and decoding boundaries](adr/0005-response-terminal-and-decoding.md)
- [ADR-0006: Offline verification boundary](adr/0006-offline-verification-boundary.md)
- [ADR-0007: Stateful Responses continuation](adr/0007-stateful-responses-continuation.md)
- [ADR-0008: Global run budget](adr/0008-global-run-budget.md)
- [ADR-0009: Profile isolation and responsibility split](adr/0009-profile-isolation-and-responsibility-split.md)
- [ADR-0010: Standard Maven source layout](adr/0010-standard-maven-source-layout.md)

## Documentation maintenance contract

Update documentation in the same change as code when any of these occur:

1. A package or dependency direction changes: update `architecture.md` and ADR-0001 or add a superseding ADR.
2. A class is added, removed, renamed, or changes responsibility: update the class catalog and relevant class diagram.
3. A runtime or protocol transition changes: update the matching sequence diagram and ADR.
4. A capability or limitation changes: update both README files and both mind-map files.
5. An English document changes materially: update its `.zh-CN.md` companion in the same change.

ADRs are append-only historical decisions. Do not rewrite an accepted decision after the architecture changes; add a new ADR that supersedes it and update the indexes.

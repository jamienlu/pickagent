# Release verification

[English](release-verification.md) | [简体中文](release-verification.zh-CN.md)

Run from the repository root with Java 21. A release candidate is acceptable only when every non-live item passes and live evidence, if required by the deployment, is produced separately under explicit approval.

## Deterministic gate

```powershell
mvn "-P!jdk-17" clean verify
mvn "-P!jdk-17,offline" clean verify exec:java
mvn "-P!jdk-17,online" clean verify
mvn "-P!jdk-17,live" -DskipITs test-compile
git diff --check
```

Acceptance evidence:

- The default, offline, and online gates run 171, 172, and 179 tests respectively, with zero failures, errors, or skips.
- Each non-live gate reports JaCoCo bundle line and branch covered ratios of `1.00` for the production classes compiled by that profile.
- The default `verify` execution generates Javadoc and fails on any warning.
- Offline output contains `ledger.proof=PASS`, `model.calls=2`, and `tool.calls=1`.
- The online source and dedicated test sets verify without making a request; the live source set compiles without making a request.
- The JAR exists and excludes offline, online-CLI, and live-test classes unless their profile is deliberately packaged.
- Every maintained English Markdown file has a same-basename `.zh-CN.md` companion.
- Relative Markdown links, fenced code blocks, trailing whitespace, and Mermaid blocks pass repository checks.
- Java source has no pure-English comments or Javadoc; every test method has the required Chinese scenario comment.
- Git status contains no unexpected generated or credential files. Preserve and report unrelated outer-repository changes rather than modifying them.

## Optional credentialed smoke

Do not run this as part of ordinary verification. Under explicit approval, provide an approved key/model and run the `live` profile with strict provider-side spend and rate limits. Record only sanitized result metadata; never record the key or sensitive content.

## Residual-risk sign-off

Before production, the owning application must accept or close these risks: process-local idempotency, no rollback of completed side effects, application-owned authorization/approval, no streaming or structured final output, and limited live latency/cost/model-behavior evidence.

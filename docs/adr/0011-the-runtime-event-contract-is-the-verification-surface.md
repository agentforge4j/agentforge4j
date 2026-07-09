# ADR-0011: The runtime event contract is the verification surface

## Status

Accepted

## Date

2026-06 (approximate — the event-contract assertion suite merged in late June 2026; recorded retrospectively 2026-07-09)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction.

## Context

A deterministic engine whose value is auditability (ADR-0001) cannot be verified by outputs alone: two runs can produce the same artifact while one silently skipped a retry, reordered a pause/resume, or bypassed a tool denial. Output-only assertions pass exactly when governance regressions are invisible. White-box assertions on runtime internals verify behaviour but couple tests to implementation, making every refactor a test rewrite. What users actually rely on — and what the framework contractually promises — is the audit event stream.

## Decision

Black-box verification asserts the runtime **event contract**: the presence, content, and ordering of audit events — tool-governance outcomes, retry counts, pause/resume ordering, failure propagation — for full workflow runs against the deterministic fake provider. The event stream is treated as public, tested API: a change that alters emitted events is a contract change, caught by the suite, never an invisible internal detail.

Event assertions live in the verification suites, not in per-workflow catalog fixtures: an earlier approach of shipping expected-event lists alongside catalog fixtures was deliberately dropped (2026-06-30) as brittle duplication — every additive event change would have invalidated every fixture, without adding assertion power the suites don't already have.

## Alternatives considered

- **Output-only assertions.** Blind to governance behaviour — precisely the regressions that matter most here.
- **White-box assertions on runtime internals.** Verifies behaviour but welds tests to implementation structure; the event stream is the stable observable.
- **Per-workflow expected-event fixtures in the catalog** (tried, removed). Duplicates the contract per fixture and makes additive event evolution a mass fixture churn; the suites assert the contract once, properly.

## Consequences

### Positive

- Governance semantics (denials, approvals, retries, pauses) are regression-tested end to end, not assumed.
- The event stream's status as public contract is enforced mechanically: emitting differently breaks a test.
- Tests survive internal refactors; only genuine contract changes fail them.

### Negative

- Additive event changes still touch verification tests — the deliberate cost of a tested contract.
- Ordering assertions require deterministic execution end to end, constraining internal concurrency choices.

### Neutral / tradeoffs

- Shipped catalog workflows are verified by scenario runs whose event assertions live in the suites; catalog fixtures themselves carry no event expectations. Nothing in this record implies catalog-fixture event coverage.

## Compatibility impact

Confirms the audit event types, their payloads, and their ordering guarantees as public contract. Event evolution is additive; removals or reorderings are breaking changes governed by the compatibility policy in force (pre-1.0: ADR-0013).

## Implementation notes

Event-contract assertions merged to `main` (commit `30705a53`); retry-count assertions (`STEP_RETRIED`) present in the black-box verification module's retry-loop tests; the suite runs full workflows against the deterministic fake provider on JDK 17. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Two retry-semantics verification sub-points (final-exception and attempt-count) remain open and tracked for the first release.

## Related documents

- ADR-0001 (the execution contract this verifies).
- ADR-0003 (tool governance — whose outcomes the suite asserts).

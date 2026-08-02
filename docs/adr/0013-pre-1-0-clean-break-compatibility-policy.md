# ADR-0013: Pre-1.0 clean-break compatibility policy

## Status

Accepted

## Date

2026-07-09 (retrospective record date — the policy has been in force throughout pre-1.0 development; no single decision date exists)

## Retrospective note

This ADR was written retrospectively to document an already accepted and consistently applied policy. It records existing practice; it did not precede it.

## Context

Before a first public release there are no external consumers, but there is a strong temptation to accumulate compatibility scaffolding anyway: deprecated shadows, back-compat constructors, bridge overloads. Every such shim carried into 0.1.0 becomes accidental public API — surface that must then be supported or broken *after* people depend on it. The cheapest moment to fix a wrong signature, schema shape, or SPI is now.

## Decision

Until 1.0, API, SPI, and schema changes are made as clean breaks: the canonical signature changes directly, and **all** call sites are updated in the same change. No deprecated shadows, no back-compat constructors, no shims, no dual paths. Additions to enums and events remain additive by nature, but nothing wrong is preserved for compatibility's sake.

## Alternatives considered

- **Deprecate-then-remove cycles.** Standard for released libraries, but pre-release it only manufactures dead surface that either leaks into 0.1.0 or forces a noisy removal pass right before launch.
- **Additive-only evolution** (never change, only add). Accumulates parallel ways to do the same thing and freezes early design mistakes into the first release.
- **Ad-hoc judgment per change.** In practice drifts toward keeping shims "just in case"; an explicit policy removes the negotiation.

## Consequences

### Positive

- 0.1.0 ships one canonical way to do each thing; no deprecated surface on day one.
- Design mistakes are corrected at full fidelity instead of being bridged around.
- Reviews are simpler: a signature change is complete in one change set or it is not done.

### Negative

- Changes fan out: a signature or schema change can touch dozens of call sites in a single change — the capability-envelope removal (ADR-0004) and the inline-blueprint removal (ADR-0010) each migrated every affected call site and definition in-change.
- Anyone building against snapshots absorbs breakage without a deprecation runway — an accepted cost while the project is pre-release.

### Neutral / tradeoffs

- The policy has an expiry: at 1.0 it ends and semantic-versioning discipline takes over. This ADR should be superseded by the post-1.0 compatibility policy at that point.

## Compatibility impact

Deliberately maximal, and deliberately confined to the pre-1.0 window: every public API, SPI, and schema surface may change without deprecation until the first release. After 1.0 this policy no longer applies.

## Implementation notes

Applied repository-wide; the clean-break removals recorded in ADR-0003, ADR-0004, and ADR-0010 each deleted the superseded surface outright and migrated all call sites in the same change. Verified as current practice on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Author the post-1.0 compatibility and deprecation policy before cutting 1.0; mark this ADR superseded by it.

## Related documents

- ADR-0018 (release management), once accepted — the versioning scheme this policy hands over to.

# ADR-0007: Greenfield rebuild of the shipped workflow catalog

## Status

Accepted (marks the seven legacy workflow bundles superseded and removed)

## Date

2026-06-30 (merge date of the removal)

## Retrospective note

This ADR was written retrospectively to document an already accepted and executed decision: the legacy catalog content was deliberately removed on 2026-06-30, before this record was authored.

## Context

The legacy catalog predated the normative bundle conventions (ADR-0006): its seven bundles lacked systematic verification scenarios, deterministic expected results, and example projects, and their internal quality varied. Retrofitting each bundle to the conventions would have cost more than rebuilding, while keeping non-conforming content on `main` meant every new rule shipped with standing violations of itself. With no external users yet, the cost of removal was near zero and only rises after release.

## Decision

Remove all seven legacy workflow bundles from the catalog in one deliberate change, leaving the catalog structurally intact but empty, and rebuild content from scratch — every rebuilt bundle conforming to the catalog conventions from its first commit. The rebuild is greenfield: new bundles are designed fresh, not ported.

## Alternatives considered

- **Incremental retrofit** (fix bundles in place, one at a time). Keeps content available throughout, but every intermediate state ships a mixed-quality catalog, and retrofit effort approached rebuild effort without the design benefit.
- **Freeze legacy content as-is, add new conforming bundles alongside.** Permanently ships two quality classes and makes the conventions aspirational rather than normative.
- **Remove and rebuild in the same change.** Couples an instant decision (removal) to long-running work (rebuild); the wipe was intentionally landed alone so the rebuild could proceed as reviewable, independent additions.

## Consequences

### Positive

- The conventions are normative in practice: nothing on `main` violates them.
- Rebuilt bundles are designed against the current step-behaviour set and validation model rather than carrying legacy shapes forward.
- Removal-then-rebuild produced clean, independently reviewable content additions.

### Negative

- Until rebuilt bundles land, a fresh install ships zero workflows and zero agents — the framework's showcase content is absent for the duration of the rebuild.
- Historical examples and references to legacy bundle ids are dead until re-pointed.

### Neutral / tradeoffs

- The removal was only cheap because it happened pre-release; this decision is not a precedent for post-release content removal, which the compatibility gate and versioning (ADR-0006) would govern instead.

## Compatibility impact

Legacy workflow and agent ids are gone and will not return under the same identifiers unless a rebuilt bundle deliberately re-adopts one. Catalog mechanics (artifact, loading, compatibility gate) are unchanged. No framework API impact.

## Implementation notes

Empty `shipped-workflows/index` and `shipped-agents/index` verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Land the first rebuilt bundles — open pull requests at the time of writing carry the first two. This status is perishable; verify current catalog state against `main` rather than this record.

## Related documents

- ADR-0006 (the artifact model and conventions the rebuild targets).

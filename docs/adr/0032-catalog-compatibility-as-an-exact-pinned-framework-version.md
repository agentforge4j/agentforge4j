# ADR-0032: Catalog compatibility as an exact pinned framework version

## Status

Accepted

## Date

2026-07-11 (retrospective record date — `release-catalog.yml`'s manifest-pin guard merged as
part of PR #68, 2026-07-11)

## Retrospective note

This ADR is written after `release-catalog.yml`'s "guard catalog manifest pins min = max = a
released framework version" check had already merged to `main`. It records the compatibility-
declaration decision that check enforces.

## Context

The catalog is independently versioned from the framework (ADR-0006, ADR-0020) but its content
(workflow and agent definitions) is not compatible with an arbitrary framework version — it
depends on specific framework capabilities and the workflow schemaVersion the framework accepts
(ADR-0022). The catalog's manifest already carries `minimumAgentForge4jVersion` and
`maximumAgentForge4jVersion` fields, but pre-release these are dev-time-only bounds (the maximum
is intentionally `null`, unbounded, so in-reactor builds against a moving framework SNAPSHOT don't
need constant manual edits). A released catalog artifact needs a stricter, verifiable statement
than "unbounded above."

## Decision

At release-tag time, `release-catalog.yml`'s guard enforces that the manifest declares an
**exact framework version**: `minimumAgentForge4jVersion == maximumAgentForge4jVersion`, both
non-null, and that value must correspond to an actual `framework-v<version>` tag that already
exists — an unbounded or unpinned manifest at release time is a guard failure, not a warning.
Range support (a catalog release genuinely compatible with a span of framework versions,
verified at both ends) is an explicitly named future follow-up, not attempted now — pinning to
exactly one version, verified to actually exist as a tag, is the only claim the current release
CI can back without a compatibility test matrix across framework versions.

Dev-time, the manifest stays intentionally unbounded — this guard fires only on a release tag
push, never during normal in-reactor development.

## Alternatives considered

- **Range compatibility from day one** (`minimumAgentForge4jVersion` ≠ `maximumAgentForge4jVersion`,
  verified at both ends). Rejected for now — verifying a range means testing the catalog against
  every framework version in that range, which needs compatibility-matrix test infrastructure
  that does not exist yet; claiming a range without verifying both ends would be an unbacked
  compatibility promise.
- **No verification, manifest self-declares whatever the author writes.** Rejected — silently
  allows an unbounded or stale-pinned manifest to reach Central, exactly the class of error the
  guard exists to catch loudly instead.
- **Pin to a version without checking the tag actually exists.** Rejected — a manifest pinning a
  framework version that was never actually released would pass a naive equality check while
  still making an unverifiable claim; checking `git rev-parse refs/tags/framework-v<version>`
  closes that gap.

## Consequences

### Positive

- A released catalog's compatibility claim is always independently verifiable — the pinned
  version is a tag that provably exists, not just a string.
- The dev-time/release-time distinction means day-to-day catalog development against a framework
  SNAPSHOT needs zero manual manifest edits; only the release-tag guard is strict.

### Negative

- A catalog release is coupled to exactly one already-released framework version — a catalog
  content-only fix cannot widen its own compatibility without a new framework release existing
  first to pin to.

### Neutral / tradeoffs

- Range support is a real capability being deliberately deferred, not rejected outright — named
  explicitly as follow-up so it is not silently forgotten.

## Compatibility impact

**Public contract.** The catalog manifest's `minimumAgentForge4jVersion`/
`maximumAgentForge4jVersion` fields carry release-time verification semantics they did not
previously have; a release with an unpinned or unbounded manifest now fails CI rather than
publishing.

## Implementation notes

`.github/workflows/release-catalog.yml`'s `release-guard` job, "Guard catalog manifest pins
min = max = a released framework version" step; manifest at
`agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/agentforge4j-catalog.json`.

## Follow-up work

Range support (verified min/max compatibility across more than one framework version) once a
compatibility test matrix across framework versions exists — named in the design as RM-2.

## Related documents

- ADR-0022 — independent versioning and schemaVersion as the compatibility contract.
- ADR-0030 — generated compatibility matrix (surfaces this pinned bound alongside schemaVersion
  support).
- ADR-0006 — shipped catalog as an independently versioned, code-free artifact.

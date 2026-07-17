# ADR-0033: NumericVersion becomes qualifier-aware, as a hard prerequisite for 1.0.0-RC1

## Status

Proposed

## Date

2026-07-10

## Context

`NumericVersion` (`agentforge4j-config-loader`'s catalog compatibility gate) deliberately strips
any `-SNAPSHOT`, pre-release, or build qualifier before comparing `major.minor.patch` — by design,
so a framework running `X-SNAPSHOT` satisfies a catalog minimum of `X` (see its own Javadoc). This
is correct for the SNAPSHOT case it was built for, but it is not strict semantic-version ordering:
`1.0.0-RC1` and `1.0.0` compare as **equal** under `NumericVersion.compare`, because the qualifier
is stripped from both before any comparison happens. Mandatory RC soak at 1.0.0 (ADR-0027) depends
on being able to tell a release candidate apart from its final release in exactly this kind of
version comparison — an RC that compares equal to its eventual release is a real correctness gap
the moment RCs are actually used.

## Decision

`NumericVersion` becomes qualifier-aware: `1.0.0-RC1 < 1.0.0`, and pre-release qualifiers order
correctly relative to each other and to the unqualified release, with regression tests covering
the RC-vs-release and RC-vs-RC cases explicitly. This is a **hard prerequisite before
`framework-v1.0.0-RC1`** can be tagged (ADR-0027) — not a nice-to-have, since the RC mechanism
that ADR depends on is unsound without it. It is explicitly **not** a 0.1.0 item: pre-1.0, no
release candidate is mandatory (ADR-0027) and the SNAPSHOT-satisfies-release behavior this class
already provides is sufficient for every pre-1.0 comparison actually performed. This lands as a
separate, later, focused PR — not bundled into this release-management-strategy implementation.

## Alternatives considered

- **Leave `NumericVersion` as-is and compare RC tags some other way** (e.g. string comparison of
  the full tag). Rejected — string comparison of `-RC1` vs `-RC2` vs no-qualifier does not sort
  correctly either (`"1.0.0" < "1.0.0-RC1"` lexicographically, backwards from the intended
  ordering), so it would need the same qualifier-aware logic anyway, just implemented in the
  wrong place.
- **Replace `NumericVersion` with a full semver library.** Considered, not decided here — the
  scope of this ADR is only that qualifier-awareness must exist before 1.0.0-RC1; whether it is
  hand-rolled (extending the existing class, consistent with its documented SNAPSHOT-equals-
  release behavior for the catalog gate specifically) or a library dependency is left to the
  implementing PR.
- **Bundle this fix into the current release-management-strategy implementation.** Rejected —
  1.0.0-RC1 is many months away on the illustrative timeline (ADR-0027's Verification note); this
  is deliberately deferred rather than adding scope to a phase that has no immediate need for it.

## Consequences

### Positive

- Removes a real correctness gap before it can actually cause an RC to be silently treated as
  equal to its eventual release by the catalog compatibility gate.
- Deferring it keeps the current implementation phase scoped to what 0.1.0 actually needs.

### Negative

- The gap exists, latent, until this lands — acceptable only because nothing pre-1.0 exercises
  RC comparison at all (ADR-0027).

### Neutral / tradeoffs

- The catalog gate's existing SNAPSHOT-satisfies-release behavior must be preserved exactly as
  documented; qualifier-awareness must add RC ordering without breaking that pre-1.0 behavior the
  class already correctly provides.

## Open questions

- Hand-rolled extension vs. a semver library dependency — left to the implementing PR, not
  decided here.

## Compatibility impact

**Internal comparison semantics only** (the catalog compatibility gate's version-ordering logic);
no public wire-format or API contract changes. Becomes load-bearing for the 1.0.0-RC1 tag-
comparison correctness once RCs are actually used.

## Verification note

Becomes Accepted once `NumericVersion` (or its replacement) has regression tests proving
`1.0.0-RC1 < 1.0.0` and correct RC-to-RC ordering, without breaking the existing SNAPSHOT-
satisfies-release test coverage. Not started — tracked as a separate, later PR, hard-gated before
`framework-v1.0.0-RC1` can be tagged.

## Related documents

- ADR-0027 — no release candidates pre-1.0; mandatory RC soak for 1.0.0 (the ADR this is a hard
  prerequisite for).
- ADR-0022 — independent versioning and schemaVersion as the compatibility contract.

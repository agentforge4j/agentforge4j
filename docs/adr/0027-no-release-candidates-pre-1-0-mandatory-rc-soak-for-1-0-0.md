# ADR-0027: No release candidates pre-1.0; mandatory RC soak for 1.0.0

## Status

Proposed

## Date

2026-07-10

## Context

Release candidates exist to catch problems before a version becomes the thing users depend on.
Pre-1.0, every 0.x release already carries an explicit "may break" expectation (ADR-0013,
ADR-0025), so an RC step adds process without changing what users can rely on. The 1.0.0 release
is different in kind: it is the point semantic-versioning guarantees begin, and it is the one
release an RC cycle is actually protecting something new.

## Decision

Pre-1.0, every release ships directly — no release-candidate stage for 0.x releases on any
track. The tag format's `-RC<n>` suffix exists in the schema now (ADR-0020) but pre-1.0 it is
used only where a track genuinely wants a soak period for a specific risky change, not as a
mandatory gate.

**`1.0.0` is the one mandatory exception**: it requires an RC soak — `framework-v1.0.0-RC1` (and
a corresponding builder RC on npm dist-tag `next`, per the illustrative timeline in the design)
for a multi-week soak period before `framework-v1.0.0` itself is tagged, with a respin (RC2, …)
if the soak surfaces a real defect.

## Alternatives considered

- **Mandatory RC for every 0.x minor.** Rejected — pre-1.0 minors already carry no stability
  guarantee; a mandatory RC stage would slow the pre-1.0 iteration this policy exists to enable,
  for a promise the version number itself already declines to make.
- **No RC requirement even at 1.0.0.** Rejected — 1.0.0 is precisely the release where semver
  compatibility guarantees start applying to real consumers; shipping it without a soak period
  risks committing to a broken contract on day one of the guarantee.

## Consequences

### Positive

- Pre-1.0 releases stay fast — no mandatory soak period slowing routine minors.
- The one release that most needs extra scrutiny (1.0.0, where the compatibility promise starts)
  gets it, deliberately and only there.

### Negative

- A pre-1.0 minor with a genuinely risky change has no mandated RC step to fall back on — the
  maintainer must judge, per release, whether an optional RC is warranted; there is no policy
  forcing that judgment.

### Neutral / tradeoffs

- The `-RC<n>` tag suffix is fully supported by the tag format and every release workflow's guard
  from day one (ADR-0020, ADR-0026), even though it is only mandatory at exactly one release —
  so using it early for a risky 0.x change, if the maintainer chooses to, requires no CI change.

## Open questions

- Whether a specific pre-1.0 minor (e.g. one bundling a significant schemaVersion batch) should
  optionally use an RC — left to per-release judgment, not decided here.

## Compatibility impact

None on runtime APIs. Release-process policy only.

## Verification note

Becomes Accepted once a real `1.0.0-RC1` has actually been tagged, soaked, and either promoted
or respun — the RC mechanics (tag format, `next` dist-tag) already exist in the release workflows
(ADR-0026), but the process has never been exercised because the project is far from 1.0.0.
`NumericVersion` becoming qualifier-aware (ADR-0033) is a hard prerequisite that must land before
this can be exercised — an RC tag is not correctly orderable against its final release without
it.

## Related documents

- ADR-0033 — NumericVersion qualifier-awareness for release candidates (hard prerequisite before
  this can be exercised).
- ADR-0025 — pre-1.0 and post-1.0 support policy.
- ADR-0020 — multi-artifact monorepo with prefixed release tracks (the tag format the `-RC<n>`
  suffix is part of).

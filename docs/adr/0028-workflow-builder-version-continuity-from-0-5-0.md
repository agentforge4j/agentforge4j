# ADR-0028: Workflow builder version continuity from 0.5.0

## Status

Accepted

## Date

2026-07-11 (retrospective record date — merged as PR #66, "Enforce supported workflow schema
versions on builder import and bump to 0.5.0", 2026-07-10)

## Retrospective note

This ADR is written after the builder's `package.json` version had already been bumped to 0.5.0
on `main`. It records the version-continuity decision that bump implements.

## Context

The workflow builder published `0.2.1`, `0.2.2`, and `0.3.0` to npm as unsupported pre-launch
test builds. `0.4.0` (step-connection UX and read-only mode, PR #151) was merged to `main` but
never published. Meanwhile, the required-`schemaVersion` work (ADR-0023) is a hard prerequisite
for the builder's first properly supported release, and it landed after 0.4.0's version number
was already in source. Something has to say plainly which version is the first one anyone should
actually treat as supported.

## Decision

**`0.5.0` is the first supported builder version.** It supersedes `0.4.0` in source directly —
`0.4.0`'s package.json version string is skipped over, not published separately, because 0.4.0's
functional changes (step-connection UX, read-only mode) are folded into 0.5.0's release alongside
the schemaVersion work (ADR-0023) that 0.5.0 exists to carry. Every published version below 0.5.0
(`0.2.1`, `0.2.2`, `0.3.0`) is retroactively declared an unsupported pre-launch test build — they
also predate the required `schemaVersion` field, so they cannot honestly interoperate with a
schemaVersion-aware framework regardless. Published npm versions are **never unpublished**
(0.2.x/0.3.0 stay resolvable) but are deprecated via npm's deprecation mechanism once 0.5.0 ships
and its downstream consumer packages have bumped past them.

## Alternatives considered

- **Publish 0.4.0 retroactively before 0.5.0.** Rejected — 0.4.0 predates the required-
  schemaVersion work; publishing it now would ship a version that cannot honestly claim
  schemaVersion support, immediately requiring a same-week 0.5.0 anyway.
- **Renumber to 0.4.0 instead of jumping to 0.5.0** (treat the unpublished 0.4.0 as if it never
  existed in source either). Rejected — the version number is already live in git history and in
  any local `npm install` against the source tree; silently reusing it risks a resolver-level
  ambiguity between "the git-history 0.4.0" and "a hypothetical future republished 0.4.0."

## Consequences

### Positive

- Exactly one version number (0.5.0) is the unambiguous "first real release" answer, with no
  gap where 0.4.0 might or might not count.
- npm's `latest` dist-tag will point at a version that both carries the UX work and honestly
  supports schemaVersion — no split release needed.

### Negative

- Anyone who happened to build directly from `main` at the 0.4.0 commit and expects that exact
  version string on npm will not find it published — an accepted, narrow-audience cost (nothing
  was ever published at that version).

### Neutral / tradeoffs

- Deprecating 0.2.x/0.3.0 rather than unpublishing them means old lockfiles resolving those
  versions keep working (npm deprecation is advisory, not removal) — consistent with "published
  versions are never unpublished."

## Compatibility impact

**Public contract (npm package version).** No published API break — this is a version-continuity
and support-window decision, not a code compatibility change.

## Implementation notes

`agentforge4j-workflow-builder/package.json` `version` field (`0.5.0`); README and SECURITY.md
supported-version statements (`agentforge4j-workflow-builder/README.md`, root `SECURITY.md`);
deprecation of pre-0.5.0 npm versions and the three consumer bumps are launch-sequence steps that
require an actual npm publish, tracked separately.

## Follow-up work

At `builder-v0.5.0`'s actual publish (RM-3 sequence): verify the `latest` dist-tag moved, bump
its downstream consumer packages to `^0.5.0`, deploy and verify those consumers, then run
`npm deprecate` against every published version below 0.5.0.

## Related documents

- ADR-0023 — required, strict workflow schemaVersion (why 0.5.0 specifically is the cutover
  point).
- ADR-0025 — pre-1.0 and post-1.0 support policy.
- `agentforge4j-workflow-builder/CHANGELOG.md` — the version history this ADR's decision is
  reflected in.

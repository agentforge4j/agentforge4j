# ADR-0026: Tag-triggered release CI and environment-gated publishing

## Status

Accepted

## Date

2026-07-11 (retrospective record date — `release-framework.yml`/`release-catalog.yml`/
`release-builder.yml`/`tag-guard.yml` merged 2026-07-10/2026-07-11)

## Retrospective note

This ADR is written after all three track release workflows and the shared tag guard had already
merged to `main`. It records the CI-shape decision those workflows implement. The GitHub
environments (`maven-central`, `npm-publish`) and branch/tag protection rulesets they depend on
are owner-managed console configuration, tracked separately as follow-up work, not code on `main`.

## Context

Manual artifact assembly and publication does not scale past the first release without becoming
an error source — a forgotten signing step, a stale build, or a version mismatch between the tag
and the published artifact are all easy to make by hand and easy to prevent with CI. Central and
npm publication also both require secrets that must never be exposed to arbitrary code triggered
by a pull request.

## Decision

Every track's release runs from a **tag push, in CI, with an environment-gated publish step**:

- Each of the three tracks (ADR-0020) has its own release workflow, sharing a common four-stage
  shape: `release-guard` (tag format, version-matches-source, branch-ancestry, committed
  release-note existence — ADR-0031) → `release-verify` (the track's full test/build matrix,
  unsigned, no secrets) → `release-publish` (signs and publishes, gated by a GitHub environment
  requiring a manual reviewer approval) → `release-announce` (creates the GitHub Release from the
  committed note source).
- `tag-guard.yml` runs on every tag push regardless of prefix and rejects anything not matching a
  known track (ADR-0020) — a belt-and-suspenders check alongside branch/tag protection rulesets
  that restrict tag creation to the owner.
- Maven Central publication starts with **`autoPublish=false`** (validated staging only; the
  final release-to-Central action is a manual portal step), to be flipped to automatic only after
  a small number of proven manual releases build confidence in the pipeline.
- Publishing secrets (`GPG_PRIVATE_KEY`, `CENTRAL_TOKEN_USERNAME`/`PASSWORD`, `NPM_TOKEN`) are
  scoped to their environment and never reachable from a workflow triggered by anything other
  than an owner-created tag push.

## Alternatives considered

- **Manual release assembly** (build and upload by hand). Acceptable for exactly one release;
  rejected as the ongoing process — see Context.
- **`autoPublish=true` from the start.** Rejected — the first several releases are exactly when a
  pipeline defect is most likely and most costly to discover after the fact on an immutable
  artifact; a manual portal step for the first few releases is a deliberate, temporary brake.
- **A single repository-variable off-switch** (`RELEASE_PUBLISH_ENABLED`) in addition to the
  environment reviewer gate. Considered in an early draft and dropped by owner ruling
  (2026-07-10): two independent switches gating the same one decision only invites drift between
  them; the environment reviewer is the single gate.

## Consequences

### Positive

- No manual artifact assembly for any of the three tracks; a tag push is the entire trigger.
- The environment-reviewer gate means no publish action is fully automatic — a human confirms the
  exact build about to become permanently immutable.
- Shared four-stage shape across all three tracks keeps the workflows easy to audit against each
  other for drift.

### Negative

- The environment-gate gives no protection against a bad build reaching `release-verify` green
  and then being approved by habit rather than genuine review — a process risk, not a technical
  one, mitigated only by the manual checklist (ADR-0031 / `docs/releases/README.md`).

### Neutral / tradeoffs

- `autoPublish=false` means every release, even routine ones after the pipeline is proven, still
  needs a manual portal action until the owner explicitly flips the switch — an intentional,
  revisitable brake, not a permanent one.

## Compatibility impact

None on runtime APIs. Release-process automation only.

## Implementation notes

`.github/workflows/tag-guard.yml`, `release-framework.yml`, `release-catalog.yml`,
`release-builder.yml`. GitHub environments `maven-central` and `npm-publish` (owner-managed,
required reviewer) and branch/tag protection rulesets are configuration, not code — see Follow-up
work.

## Follow-up work

Owner-managed, not part of any implementing PR: create the `maven-central` and `npm-publish`
GitHub environments with a required reviewer and the secrets named above; create the `main` /
`release/*` / tag-prefix protection rulesets (ADR-0018 §13-equivalent); delete the existing
classic branch-protection rule once the rulesets are active; flip `autoPublish=true` after 2–3
proven manual releases.

## Related documents

- ADR-0020 — multi-artifact monorepo with prefixed release tracks.
- ADR-0031 — per-track changelogs and committed release-note sources (what `release-guard`
  checks for and `release-announce` publishes).
- ADR-0018 — release management and publication sequencing.

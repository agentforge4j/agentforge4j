# ADR-0021: Release branching model

## Status

Proposed

## Date

2026-07-10

## Context

Once a track has shipped a release, a bug found against it needs a fix path that does not force
an unrelated feature freeze on `main`, and large uncertain work needs somewhere to live without
either blocking `main` or being abandoned mid-flight. No branching model beyond `main` exists on
this repository today, because nothing has released yet.

## Decision

Adopt a Spring-Boot-style branching model:

- **`main`** is always the development line for the next release, on every track.
- **`release/<track>-0.N.x`** branches are created lazily, only when a patch is actually needed
  against an already-released minor — not pre-emptively at every release. They receive fixes
  only; forward-port PRs carry those fixes back to `main` (ADR-0024).
- **`initiative/*`** branches are disposable isolation for work whose direction is not yet
  settled — large or experimental changes that would otherwise sit half-finished on `main` or
  force premature design commitments. They merge into `main` only once accepted, or are deleted
  if abandoned.

A long-lived `development/0.N` line, or treating `main` itself as the stable branch with a
separate long-lived development branch, was explicitly considered and rejected — see Alternatives.

## Alternatives considered

- **Stable-main model** (`main` = latest stable, development happens on a long-lived branch).
  Rejected by owner ruling 2026-07-10: inverts the convention Java/Spring contributors already
  expect, and a solo-maintainer project gets no benefit from the extra indirection.
- **Long-lived `development/0.N` branches** (one per minor, cut in advance). Rejected: creates
  permanent merge-forward burden between `development/0.N` and `main` for every change, for a
  branch that in practice tracks `main` almost exactly until the next release.
- **No release branches at all** (patch by re-tagging a fixed `main`). Rejected: forces a patch
  release to carry every unrelated change merged to `main` since the last release, defeating the
  purpose of a patch.

## Consequences

### Positive

- Patch releases stay minimal — only the fix, not incidental `main` drift.
- `initiative/*` gives uncertain work a name and a lifecycle without polluting `main`'s history
  with half-finished direction changes.
- No branch exists until it is actually needed — zero standing maintenance cost pre-release.

### Negative

- A release branch's existence is not visible until the first patch is needed; a contributor
  looking for "where do I fix 0.1.x" before any patch has happened finds no branch yet — mitigated
  by CONTRIBUTING.md documenting the convention regardless of whether a branch currently exists.

### Neutral / tradeoffs

- Every patch requires a forward-port PR review (ADR-0024) rather than a direct cherry-pick —
  more process per patch, deliberately, to keep `main` and the release branch from silently
  diverging in behavior.

## Open questions

None — the model itself is fully specified. What remains open is only *when* the first
`release/framework-0.1.x` branch gets created, which is a consequence of when the first patch is
actually needed, not a decision this ADR defers.

## Compatibility impact

None on runtime APIs. Repository branching convention only.

## Verification note

Becomes Accepted once a `release/<track>-0.N.x` branch has actually been created for a real
patch and a forward-port PR (ADR-0024) has carried that fix back to `main` — no such branch
exists yet, because no track has shipped a release to patch.

## Related documents

- ADR-0020 — multi-artifact monorepo with prefixed release tracks (the tracks this model applies
  to).
- ADR-0024 — patch release forward-port flow (how fixes travel from a release branch to `main`).
- `CONTRIBUTING.md` — Releases section.

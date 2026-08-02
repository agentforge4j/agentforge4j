# ADR-0031: Per-track changelogs and committed release-note sources

## Status

Accepted

## Date

2026-07-11 (retrospective record date — the per-track changelog split and the release-note
convention are implemented in this same change)

## Retrospective note

This ADR is written alongside the change that implements it: the per-track `CHANGELOG.md` split
and the `docs/releases/<track>/<version>.md` convention (including the mandatory-headings guard
already coded into all three release workflows since ADR-0026).

## Context

The single root `CHANGELOG.md` predates the three-track split (ADR-0020) and was left undefined
for the catalog and builder tracks — the builder in particular had accumulated real, undated
history with no per-track home. Separately, each release workflow's `release-announce` stage
needs release-note *content* to publish as the GitHub Release body, and an immutable published
artifact (ADR-0023's schemaVersion-style reasoning applies here too) must not depend on content
written *after* the tag exists — the content has to already be committed and correct at tag time,
or a CI failure that catches a missing note only fires after the release commitment is already
made.

## Decision

**Per-track changelogs.** The root `CHANGELOG.md` becomes the framework track's changelog,
gaining a header naming the split and linking the other two. `agentforge4j-workflow-builder/
CHANGELOG.md` and `agentforge4j-workflows-catalog/CHANGELOG.md` are each an independent
Keep-a-Changelog file. All three are curated at release time from the same notes as that
release's GitHub Release — the Release is the canonical public announcement; the changelog files
are the in-repo historical record.

**Committed release-note sources.** Every release is preceded by a committed file at
`docs/releases/<track>/<version>.md`, containing five mandatory headings — Highlights, Breaking
changes, Compatibility, Coordinates, Support — checked for existence (not content) by every
`release-guard` job (ADR-0026) **before** the tag's verify/publish stages run. `release-announce`
publishes this file **verbatim** as the GitHub Release body. Because the guard runs before
publish and the file is already committed in the tagged tree, a missing or incomplete note is a
release-blocking CI failure caught before anything is signed or published — never a race against
publish-time content.

## Alternatives considered

- **Single shared `CHANGELOG.md` for all three tracks.** Rejected — a reader watching only the
  builder track has no way to filter out framework-only entries without per-track separation;
  this is also the root cause of the builder's own changelog history sitting undefined for months
  before this decision.
- **Release notes authored at tag time, not committed beforehand** (e.g. typed into the GitHub
  Release UI after `release-publish` succeeds). Rejected — decouples note quality/completeness
  from the automated guard entirely, and risks an empty or thin Release body for an already-
  immutable artifact with no CI signal that anything was wrong.
- **Generate release notes automatically from commit messages** (e.g. GitHub's auto-generated
  notes). Rejected as the primary mechanism — commit messages are written for reviewers, not end
  users; a curated note under the five mandatory headings is a different, deliberately
  user-facing document.

## Consequences

### Positive

- A release can never publish with a missing or structurally incomplete note — the guard fails
  closed before signing/publishing anything.
- Each track's changelog history is now legible on its own, including the builder's previously
  undated/fragmented history, corrected in this same change.
- The GitHub Release body is guaranteed to match the committed, reviewed note source exactly —
  no drift between "what was reviewed" and "what got published."

### Negative

- Writing a real release note (not just a commit-message dump) is manual authoring work per
  release, on top of the changelog entries — an accepted cost for a user-facing document.

### Neutral / tradeoffs

- The guard checks heading *existence* only, not content quality — a deliberately cheap
  mechanical check; content quality remains a human review responsibility, not something CI can
  meaningfully enforce.

## Compatibility impact

None on runtime APIs. Documentation/release-process convention only.

## Implementation notes

`CHANGELOG.md` (framework, root), `agentforge4j-workflow-builder/CHANGELOG.md`,
`agentforge4j-workflows-catalog/CHANGELOG.md`; `docs/releases/README.md` (the convention and
maintainer checklist); the mandatory-headings guard step in each of `release-framework.yml`,
`release-catalog.yml`, `release-builder.yml`'s `release-guard` job; `release-announce`'s
`--notes-file` publish step in the same three workflows.

## Related documents

- ADR-0026 — tag-triggered release CI and environment-gated publishing (the guard/announce
  stages this ADR's checks and publish step live in).
- ADR-0020 — multi-artifact monorepo with prefixed release tracks.
- `docs/releases/README.md` — the convention and maintainer checklist this ADR describes.

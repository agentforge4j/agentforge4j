# ADR-0030: Generated compatibility matrix

## Status

Proposed

## Date

2026-07-10

## Context

Independent track versioning plus a wire-format `schemaVersion` contract (ADR-0022) means "does
catalog X work with framework Y, and which builder version can edit both" is answerable in
principle from each artifact's own manifest data — but only if something actually assembles that
answer. Left as three separately-versioned artifacts with no aggregated view, a user has to
manually cross-reference each artifact's declared bounds, which does not scale past the first
release and invites hand-maintained documentation drifting from what the artifacts actually
declare.

## Decision

The compatibility matrix is **generated, never hand-maintained**, from the artifacts' own
manifest data — each track's published version, its declared `schemaVersion` support (or, for
the catalog, its `minimumAgentForge4jVersion`/`maximumAgentForge4jVersion` bounds, ADR-0032) — and
surfaced two ways: as a human-readable table on the docs site, and as a machine-readable
`release.json` artifact. Both are regenerated at release time (checklist step, `docs/releases/
README.md`) directly from the manifests, so the matrix cannot state a compatibility claim no
artifact's own manifest actually backs.

## Alternatives considered

- **Hand-maintained compatibility table** (a markdown or wiki page manually updated per release).
  Rejected — the exact "docs describe something that isn't true" failure mode ADR-0018's
  docs-before-artifacts invariant exists to prevent; a hand-maintained table drifts the moment a
  release is cut without remembering to update it.
- **No matrix at all, direct users to each artifact's own manifest.** Rejected — technically
  correct but unusable in practice; cross-referencing three separate manifest files by hand for
  every combination is exactly the friction a matrix removes.

## Consequences

### Positive

- The matrix can never assert a compatibility claim inconsistent with what the artifacts
  themselves declare, because it is derived from them, not authored independently.
- `release.json` gives tooling (a future dependency-update bot, an IDE plugin) a machine-readable
  source instead of needing to scrape the docs site.

### Negative

- Requires build tooling to actually generate both outputs from manifest data — not yet built.

### Neutral / tradeoffs

- Regeneration is a release-checklist step (`docs/releases/README.md`), not fully automated in
  CI yet — a manual trigger per release until the generation tooling is proven, matching the same
  cautious-rollout posture as `autoPublish=false` (ADR-0026).

## Open questions

- Exact generation mechanism and where it runs (a docs-build step vs. a dedicated script invoked
  from the release checklist) — folds into the docs-site work (docs phases 5b/5c), not decided
  here.

## Compatibility impact

None on runtime APIs. Documentation/tooling artifact only.

## Verification note

Becomes Accepted once the generation tooling exists and has actually produced a real matrix and
`release.json` from real released-artifact manifest data — neither exists yet. This work is
explicitly out of the release-management-strategy implementation's own scope; it folds into the
separate docs-site workstream (docs phases 5b/5c), which is still unmerged.

## Related documents

- ADR-0022 — independent versioning and schemaVersion as the compatibility contract (the data
  the matrix is generated from).
- ADR-0032 — catalog compatibility as an exact pinned framework version (the catalog's
  contribution to the matrix).
- `docs/releases/README.md` — the release checklist step that triggers regeneration.

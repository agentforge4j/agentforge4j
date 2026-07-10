# ADR-0018: Release Management and Publication Sequencing

## Status

Proposed

## Date

2026-07-10 (proposal date)

## Context

The first public release (0.1.0) publishes immutable artifacts to Maven Central. Immutability drives everything: whatever an artifact references — documentation URLs, project site, catalog coordinates — must exist and be correct *before* publication, because it can never be corrected in the published artifact.

Constraints and forces:

- Central publication requires complete POM metadata, GPG-signed artifacts, and sources/javadoc jars.
- The shipped workflow catalog is independently versioned from the framework; its published POM must stand alone (no snapshot parent leaking into consumers).
- The root README is the project's front door; it must describe the release truthfully, so it can only be finalized once everything it describes is real.
- Pre-1.0, the compatibility policy is clean-break (ADR-0013); the release process must not imply stability guarantees the version does not carry.

## Decision

Adopt a strict publication sequencing rule for the coordinated first public launch (0.1.0), and the supporting release machinery:

1. **Documentation and project site go live first** — the docs site and project domain must be serving before any artifact referencing them is published.
2. **Maven Central publication second** — the framework and the catalog, as signed artifacts with full metadata, sources, and javadoc, published via the Central portal tooling from a dedicated release profile.
3. **Builder npm publication third** — the workflow builder publishes to npm once the site is live and Central carries the framework and catalog; a documentation surface (the project site's visualizer embed) pins the supported builder version, so it follows the same docs-before-artifacts invariant as the other two.
4. **Root README lands last** — it merges only when every other release gate is satisfied, as the final gate.

After the initial launch, this four-step ordering no longer applies: each track releases independently, subject only to the permanent docs-before-artifacts invariant — any documentation surface an artifact references must be live before that artifact publishes.

Supporting decisions:

- **Three independently versioned, prefix-tagged release tracks**: `framework-v*` (the full Maven reactor, to Central), `catalog-v*` (the shipped workflow catalog, to Central with a flattened POM so its published POM stands alone), and `builder-v*` (the workflow builder, published to npm). Post-launch, each track iterates on its own cadence; no track's release blocks another's.
- **Release automation** runs in CI from tags, one workflow per track; no manual artifact assembly.
- **Versioning**: 0.x with the clean-break policy; the version itself signals contract fluidity.

## Alternatives considered

1. **Publish first, fix docs after**: conventional for mutable hosting, fatal for immutable artifacts — rejected as the anti-goal this ADR exists to prevent.
2. **Single-version reactor release** (catalog pinned to framework version): simpler, but couples content iteration speed to framework release cadence.
3. **Manual releases without CI automation**: acceptable for exactly one release, an error source afterwards.

## Consequences

### Positive

- Published artifacts never reference dead or wrong destinations.
- Catalog content can evolve faster than the framework.
- The README-last rule forces release honesty: nothing is described before it exists.

### Negative

- The sequencing rule serializes the critical path; docs/site delays block publication.
- Independent catalog versioning adds a compatibility dimension (catalog ↔ framework minimum version) that must be stated and checked.

### Neutral / tradeoffs

- Signing and portal setup are one-time costs with external lead time (key distribution, namespace verification).

## Open questions

- Whether one optional integration module ships in 0.1.0 despite a module-naming caveat in an upstream dependency, or is deferred.
- The exact shape of release CI (tag-triggered jobs, staging validation, rollback of a failed publication attempt).
- Cut point for the release branch/tag relative to the remaining gate items.

## Compatibility impact

- **API/runtime/workflow definitions**: none — process only.
- **Configuration**: build-level (release profile, signing, publication plugin, catalog flatten).
- **Docs/examples**: docs site and project domain become release-blocking deliverables; the README gains its finalized release-state content.
- **Users**: first consumable coordinates on Central; catalog consumable as an independent dependency.

## Verification note

Becomes Accepted once the release profile produces complete, signed, portal-valid artifacts in a dry run and every sequencing gate is reachable. POM metadata and the release profile have landed; no release, deploy, or publish workflow exists yet in CI (confirmed against current `main`), and signing and the catalog deploy job remain open. No prefixed release tags (`framework-v*`, `catalog-v*`, `builder-v*`) exist yet either. The wire-format `schemaVersion` field referenced in ADR-0001's follow-up work is a decided requirement — not an open question — but it remains unimplemented on `main`: it must land in the workflow schema before framework 0.1.0 is cut.

## Follow-up work

If portal validation or catalog POM flattening reveals structural POM changes with wider impact, this decision needs revision. Only the automation shape may be rejected outright, never the sequencing rule itself — the docs-before-artifacts ordering is the invariant this ADR exists to establish.

## Related documents

- ADR-0006 — independently versioned code-free catalog artifact
- ADR-0009 — ServiceLoader/JPMS module policy (the module-naming caveat)
- ADR-0013 — pre-1.0 compatibility policy
- `docs/adr/README.md` — index

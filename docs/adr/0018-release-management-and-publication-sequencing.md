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

Supporting decisions (each now has its own standalone, detailed record — see Related documents;
this summary is kept for context and is not itself superseded, only elaborated):

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

Becomes Accepted once the release profile produces complete, signed, portal-valid artifacts in a dry run and every sequencing gate is reachable.

**Updated 2026-07-11 (editorial — reflects current state, does not change the decision):** the
release/publication *machinery* this ADR anticipated is now built — `release-framework.yml`,
`release-catalog.yml`, `release-builder.yml`, and `tag-guard.yml` all exist on `main` (ADR-0026),
and the wire-format `schemaVersion` field is implemented and required (ADR-0023). What remains
open is specifically the **sequencing** this ADR is actually about: the docs site has not been
confirmed live, no Central or npm publish has happened yet (`autoPublish=false`, still awaiting
the first real release), no prefixed release tags have been pushed, and the root README has not
yet landed as the final gate. The GitHub environments/rulesets the release workflows depend on
are owner-managed configuration, not yet confirmed created.

## Follow-up work

If portal validation or catalog POM flattening reveals structural POM changes with wider impact, this decision needs revision. Only the automation shape may be rejected outright, never the sequencing rule itself — the docs-before-artifacts ordering is the invariant this ADR exists to establish.

## Related documents

- ADR-0006 — independently versioned code-free catalog artifact
- ADR-0009 — ServiceLoader/JPMS module policy (the module-naming caveat)
- ADR-0013 — pre-1.0 compatibility policy
- ADR-0020 — multi-artifact monorepo with prefixed release tracks (standalone elaboration of this
  ADR's "three tracks" supporting decision)
- ADR-0023 — required, strict workflow schemaVersion (the field this ADR named as a decided,
  then-unimplemented prerequisite)
- ADR-0026 — tag-triggered release CI and environment-gated publishing (standalone elaboration
  of this ADR's "release automation" supporting decision)
- `docs/adr/README.md` — index

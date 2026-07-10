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

## Proposed decision

Adopt a strict publication sequencing rule and the supporting release machinery:

1. **Documentation and project site go live first** — the docs site and project domain must be serving before any artifact referencing them is published.
2. **Maven Central publication second** — signed artifacts with full metadata, sources, and javadoc, published via the Central portal tooling from a dedicated release profile.
3. **Root README lands last** — it merges only when every other release gate is satisfied, as the final gate.

Supporting decisions:

- **Independent catalog releases**: the catalog publishes on its own tag-driven cadence with a flattened POM, so catalog versions can iterate without framework releases.
- **Release automation** runs in CI from tags; no manual artifact assembly.
- **Versioning**: 0.x with the clean-break policy; the version itself signals contract fluidity.

## Alternatives under consideration

1. **Publish first, fix docs after**: conventional for mutable hosting, fatal for immutable artifacts — rejected as the anti-goal this ADR exists to prevent.
2. **Single-version reactor release** (catalog pinned to framework version): simpler, but couples content iteration speed to framework release cadence.
3. **Manual releases without CI automation**: acceptable for exactly one release, an error source afterwards.

## Expected consequences

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

## Decision criteria

- **Accept** when the release profile produces complete, signed, portal-valid artifacts in a dry run and the sequencing gates are all reachable.
- **Revise** if portal validation or catalog POM flattening reveals structural POM changes with wider impact.
- **Reject** only the automation shape, not the sequencing rule — the docs-before-artifacts ordering is the invariant.

## Compatibility impact

- **API/runtime/workflow definitions**: none — process only.
- **Configuration**: build-level (release profile, signing, publication plugin, catalog flatten).
- **Docs/examples**: docs site and project domain become release-blocking deliverables; the README gains its finalized release-state content.
- **Users**: first consumable coordinates on Central; catalog consumable as an independent dependency.

## Implementation outline

1. Complete portal-mandatory POM metadata and the release profile (done in part).
2. Signing setup and publication plugin wiring; dry-run validation.
3. Tag-driven release CI for the framework; separate tag-driven catalog deploy with POM flattening.
4. Execute the sequence: docs/site live → publication → README merge.

**Verification note:** POM metadata and the release profile have landed; no release, deploy, or publish workflow exists yet in CI, and signing and the catalog deploy job remain open. The wire-format `schemaVersion` question referenced in ADR-0001's follow-up work is also still unresolved. Confirm against `main` and CI configuration before moving this ADR to Accepted.

## Related documents

- ADR-0006 — independently versioned code-free catalog artifact
- ADR-0009 — ServiceLoader/JPMS module policy (the module-naming caveat)
- ADR-0013 — pre-1.0 compatibility policy
- `docs/adr/README.md` — index

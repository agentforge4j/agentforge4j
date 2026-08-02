# ADR-0022: Independent track versioning, with schemaVersion as the sole compatibility contract

## Status

Accepted

## Date

2026-07-11 (retrospective record date — the decision was ruled 2026-07-10; the implementing
loader and builder changes merged the same day as PR #63 and PR #66)

## Retrospective note

This ADR is written after the workflow schema's `schemaVersion` requirement (ADR-0023) and the
workflow builder's `supportedSchemaVersions` field had already merged to `main`. It records the
versioning-model decision those changes implement.

## Context

Three independently versioned tracks (ADR-0020) means the framework's version number, the
catalog's version number, and the builder's version number carry no relationship to each other —
`framework-v0.3.0` says nothing about which catalog or builder versions it works with. Something
has to be the actual compatibility signal, or "does this catalog work with this framework"
becomes a support question with no authoritative answer.

## Decision

Track version numbers are **independent and carry no compatibility meaning by themselves.**
Compatibility between tracks is declared through a single, separate mechanism:

- The workflow wire format carries a required integer **`schemaVersion`** field (ADR-0023) —
  the sole compatibility contract between anything that produces or consumes a workflow
  definition.
- Declaration direction is **one-way**: a consumer (the framework's loader, the builder) declares
  the set of schemaVersions it supports; a producer (a workflow document, an export) simply
  states the version it was written at. Nothing negotiates or auto-upgrades.
- Consumers declare supported-version **sets**, not a single value — e.g. the builder's
  `package.json` carries `agentforge4j.supportedSchemaVersions: [1]`, an array, because a single
  framework or builder release may need to accept more than one schemaVersion at once during a
  migration window.
- The catalog is the one exception requiring its own additional compatibility field, since it
  binds to a specific framework version rather than a wire format — see ADR-0032.

## Alternatives considered

- **Lock-step versioning** (all three tracks share one version number). Rejected — reintroduces
  the coupling ADR-0020 exists to remove; a catalog content fix would force a framework version
  bump for no framework-side change.
- **Semver-range compatibility declarations between tracks** (e.g. catalog declares
  `framework: ^0.3.0`). Rejected for the wire format specifically: two frameworks satisfying the
  same semver range are not guaranteed to accept the same document shape unless the format itself
  is versioned — semver ranges describe API compatibility, not wire-format compatibility.

## Consequences

### Positive

- Compatibility questions have one authoritative answer: does the consumer's supported-version
  set contain the document's schemaVersion. No cross-referencing three independent version
  numbers.
- A framework release can widen its supported set (accept an old and a new schemaVersion at
  once) without any coordinated multi-track release.

### Negative

- schemaVersion becomes a second version-like number contributors must understand, distinct from
  the track's own semver — the "which version do I report against" CONTRIBUTING.md section and
  the docs-site compatibility matrix (ADR-0030) both exist specifically to keep this legible.

### Neutral / tradeoffs

- One-way declaration means a producer has no way to ask "will this document be accepted" other
  than the consumer's own load-time check — acceptable, since load-time rejection is fast and
  the error names both versions explicitly (ADR-0023).

## Compatibility impact

**Public contract.** `schemaVersion` is a required field on every workflow document; consumer
supported-version sets are a new declared contract (`package.json`
`agentforge4j.supportedSchemaVersions`, the loader's internal supported-version constant).

## Implementation notes

`agentforge4j-schema/.../WorkflowSchemaVersion.java` (framework's supported version); loader
enforcement in `agentforge4j-config-loader`'s `BaseWorkflowBundleLoader` read path; builder's
`SUPPORTED_WORKFLOW_SCHEMA_VERSIONS` constant and `package.json`'s
`agentforge4j.supportedSchemaVersions` field (`agentforge4j-workflow-builder/src/validation/
schemaValidator.ts`).

## Related documents

- ADR-0023 — required, strict workflow schemaVersion (the mechanism this ADR's contract rests
  on).
- ADR-0032 — catalog compatibility as an exact pinned framework version (the one track that
  needs an additional, non-wire-format compatibility field).
- ADR-0030 — generated compatibility matrix.

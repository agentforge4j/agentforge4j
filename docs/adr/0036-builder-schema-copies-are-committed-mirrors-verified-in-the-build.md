# ADR-0036: Builder schema copies are committed mirrors, verified in the build

## Status

Accepted

## Date

2026-07-31 (record date — this ADR is written alongside the change that implements it)

## Retrospective note

This documents a decision recorded in the same change that puts it in force, in the manner of
ADR-0031. Nothing here describes a future intention: the mirror table, the synchronisation
script and the verification gate all land together.

## Context

`agentforge4j-schema` owns the JSON schema documents the framework publishes. The workflow
builder ships in the browser and cannot read them from a Maven module at runtime, so it carries
its own copies. Those copies had accumulated three different arrangements at once — one schema
generated into a git-ignored directory at build time, others committed by hand, one with no
recorded provenance at all — and no mechanism related any copy back to its source. Two had
silently gone stale. One of the two mattered: the builder's copy of the blueprint schema still
permitted a root-level `kind` property the canonical document had dropped, so every loop body the
builder exported was invalid against the framework's own published schema, and nothing failed
because the runtime ignores the field.

The generated-and-ignored arrangement is what allowed that to go unnoticed for so long: an
artifact nobody can see in a diff is an artifact nobody reviews.

## Decision

**Every builder-side schema copy is a committed, byte-for-byte mirror of a canonical document in
`agentforge4j-schema`, and the build fails when a copy stops matching its source.**

Three properties follow from that, and each is a deliberate part of the decision:

- **Committed, not generated.** The copies live under version control, so a canonical schema edit
  and its downstream effect on the published package appear in the same diff and a reviewer sees
  both halves at once. No builder schema is git-ignored.
- **Declared in one table.** `scripts/schema-mirrors.mjs` is the single statement of which files
  are mirrors and where each comes from. Each row is classified `MIRROR` (a copy, where any
  difference is drift by definition) or `BUILDER_OWNED` (genuinely builder-authored, with no
  canonical counterpart — there are none today). The table is cross-checked against the directory
  in both directions, so neither an undeclared copy nor a row pointing at a deleted file passes
  silently.
- **Verified, not repaired, during the build.** `build`, `typecheck` and `test` run
  `verify-schema-mirrors.mjs`, which *fails* on drift. Synchronisation is a separate, explicitly
  invoked `sync-schema` command. A build that re-synchronised first could never observe the drift
  it exists to catch.

Because the gate lives in the builder's own build, `agentforge4j-schema/` is also a trigger path
for the `builder` CI job: a canonical-only edit must not skip the job that would have caught it.

## Alternatives considered

- **Keep generating the copies at build time from the canonical directory (the prior arrangement
  for one schema).** Rejected — it makes drift structurally impossible to *observe*, which is not
  the same as making it impossible to *have*: the generated output never appears in a diff, so a
  canonical change that alters what the published package validates against passes review
  unseen, and the shipped npm artifact's contents are not reproducible from the tagged tree
  alone.
- **Publish the schemas as an npm package the builder depends on.** Rejected for now — it is the
  structurally cleaner answer and remains open, but it means a second release track, a version
  range to keep aligned with the framework track, and a publish step between editing a schema and
  being able to use it. That is disproportionate to four files consumed by exactly one package.
- **Have the builder validate against the canonical files directly at build time, with no copy.**
  Rejected — the schemas are bundled into the published browser artifact, so a copy exists in the
  package regardless; the only question is whether it is reviewable in the repository first.
- **Check the mirrors in a dedicated CI step rather than in `build`/`typecheck`/`test`.**
  Rejected — the gate would then not fire for a developer running the build locally, and the first
  signal of drift would be a failed pull request rather than a failed command.

## Consequences

### Positive

- A canonical schema change that forgets to update the builder fails the build, locally and in
  CI, instead of shipping a package that validates against a contract the framework no longer
  publishes.
- The published artifact's schema content is reviewable in the diff and reproducible from the
  tagged tree.
- Adding a fifth schema is a row in the table, not a new branch of copy logic.

### Negative

- Updating a canonical schema is a two-step operation: edit, then run `sync-schema`. The gate
  names the remedy in its failure output, but the step is real and cannot be skipped.
- The `builder` CI job now also runs for changes confined to `agentforge4j-schema/`, which costs
  job time on changes that will usually turn out not to affect the builder.

### Neutral / tradeoffs

- Byte-for-byte comparison, rather than semantic JSON comparison, means a formatting-only change
  to a canonical document is reported as drift. This is intended: the mirror is a copy, and
  "copy" is a simpler contract to reason about than "equivalent". `.gitattributes` normalises
  line endings repository-wide, so the comparison is stable across platforms.
- The `BUILDER_OWNED` classification has no members today. It exists so that a future exception
  has to be declared deliberately rather than appear as an unexplained difference.

## Compatibility impact

Indirect, and in the direction of correctness. Correcting the builder's blueprint schema exposed
that the exporter had been emitting a root-level `kind` property no canonical schema has ever
declared; it no longer does. Loop bodies exported by earlier builder versions remain loadable —
the runtime ignores the field and never schema-validates a blueprint body at load — so no
previously exported bundle stops working. No public API, extension SPI or `schemaVersion` value
changes.

## Implementation notes

`agentforge4j-workflow-builder/scripts/schema-mirrors.mjs` holds the table, the drift check
(`collectDrift`) and the all-or-nothing copy (`syncMirrors`), with injectable roots so both can
be exercised against fixture directories rather than only the real tree.
`scripts/verify-schema-mirrors.mjs` and `scripts/sync-schema.mjs` are the two command lines over
it, wired to the `verify-schemas` and `sync-schema` npm scripts. The mirrors themselves are in
`agentforge4j-workflow-builder/src/schemas/`; the canonical documents are in
`agentforge4j-schema/src/main/resources/schema/`. `tests/schemaMirrors.test.ts` carries one
negative control per schema, so no mirror can be silently unchecked.

## Related documents

- [ADR-0010](0010-blueprints-execute-by-file-reference-only.md) — the blueprint contract whose
  canonical shape the builder's copy had drifted from.
- [ADR-0022](0022-independent-versioning-and-schemaversion-as-the-compatibility-contract.md) —
  `schemaVersion` as the compatibility contract these documents define.
- [ADR-0030](0030-generated-compatibility-matrix.md) — the neighbouring decision that content
  derived from a source of truth is generated from it rather than hand-maintained.

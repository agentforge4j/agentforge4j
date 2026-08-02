# ADR-0023: Required, strict workflow schemaVersion

## Status

Accepted

## Date

2026-07-11 (retrospective record date — merged as PR #63, "Require an explicit schemaVersion on
every workflow definition", 2026-07-10)

## Retrospective note

This ADR is written after the schema change, the loader enforcement, and the tree-wide sweep of
every shipped and fixture `workflow.json` had already merged to `main`. It records the decision
that change implements.

## Context

`workflow.schema.json` keeps `additionalProperties: false` (a strict schema), which means any
wire-format change is potentially breaking for a document written against an older shape. Without
an explicit version field on the document itself, a loader has no way to tell "old document,
still valid" apart from "wrong document, coincidentally schema-valid" — and no way to name which
version mismatched when it fails. This is also a hard prerequisite for the framework's first
public release and for the `.org` site's embedded workflow visualizer, both of which need a
reliable way to reject documents from a future, unknown wire format instead of misinterpreting
them.

## Decision

`schemaVersion` is a required, integer field on every `workflow.json` document (`kind: WORKFLOW`
root documents only — `*.blueprint.json` and agent/artifact documents are not versioned
separately; one schemaVersion names the whole bundle wire language, matching
`WorkflowSchemaVersion` being the single contract). It increments on **every externally visible
wire-format change, additive changes included** — not only breaking ones — batched at most once
per framework minor release. Enforcement is a **wire-envelope check in the loader's read path**,
not a new `WorkflowDefinition` record component: the loader reads the document tree, fails closed
when `schemaVersion` is missing, non-integer, or not in the framework's supported set, and names
both the document's version and the supported set in the error. The workflow builder mirrors this
on import (rejecting an unsupported schemaVersion before conversion to its internal draft shape)
and stamps the current schemaVersion on every export.

## Alternatives considered

- **New `WorkflowDefinition` record component** carrying schemaVersion through the domain model.
  Rejected (Unknown U2, option B): a breaking constructor sweep across ~50+ construction sites,
  colliding with the separate pending constructor-audit/Builder work, for no behavioral benefit
  over a read-path check — schemaVersion is a wire-envelope concern, not a runtime domain concept.
- **Breaking-changes-only increments** (the original rev-2 rule). Rejected (RM-1 ruling,
  supersedes rev-2): the same schemaVersion number could then name two mutually incompatible
  strict schemas if an additive-but-still-`additionalProperties:false`-relevant field were added
  without a bump — batching per framework minor instead of per-change keeps the number from
  becoming release-cadence noise while still closing that gap.
- **No versioning, rely on `additionalProperties: false` alone.** Rejected — a strict schema
  detects an invalid document; it cannot express "this is a valid document from a format we no
  longer speak," which is the actual failure mode this ADR closes.

## Consequences

### Positive

- A version mismatch fails loudly and specifically (both versions named), never silently
  misinterpreted or silently accepted.
- The wire-envelope approach required zero changes to `WorkflowDefinition` or its ~50+
  construction sites.
- Closes both the framework 0.1.0 prerequisite and the `.org` embed blocker with one change.

### Negative

- Every workflow.json document in the repository (shipped catalog, fixtures, testkit resources,
  the standalone examples tree) needed a one-line sweep to add the field — a ~40–50 file
  mechanical change, done once, and now a permanent authoring requirement for every new workflow.

### Neutral / tradeoffs

- Concurrent PRs that add workflow JSON without `schemaVersion` (tracked as a named follow-up for
  any in-flight PR at the time this landed) get a one-line-per-file fix on rebase — the
  fail-closed check makes any miss loud rather than a silent gap.

## Compatibility impact

**Public contract.** `schemaVersion` is now a required field in the workflow wire format;
existing documents without it are rejected. This is the wire-format compatibility contract ADR-
0022 rests on.

## Implementation notes

`agentforge4j-schema/src/main/resources/schema/workflow.schema.json` (root `required` +
`properties`); `WorkflowSchemaVersion` (constant + Javadoc); loader check in
`agentforge4j-config-loader`'s `BaseWorkflowBundleLoader` read path (`ClasspathWorkflowLoader`,
`FileSystemWorkflowLoader`); builder mirror in `agentforge4j-workflow-builder/src/validation/
schemaValidator.ts` and export stamping in `src/io/browser/download.ts` / `zip.ts`.

## Related documents

- ADR-0022 — independent versioning and schemaVersion as the compatibility contract.
- ADR-0018 — release management and publication sequencing (named this as a decided, not-yet-
  implemented prerequisite before it landed).

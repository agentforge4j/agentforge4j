# ADR-0010: Blueprints execute by file reference only

## Status

Accepted (supersedes inline blueprint execution)

## Date

2026-06-22 (merge date of the removal; recorded retrospectively 2026-07-09)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction. Inline blueprint execution had already been removed when this record was authored.

## Context

Blueprints — reusable step sequences — could originally appear in a workflow in two forms: referenced as a standalone file, or defined inline inside a workflow's `steps()` array. Two representations of the same executable concept meant two loading paths, two validation paths, and definitions whose reusable parts might or might not actually be reusable (an inline blueprint is invisible to every other workflow). Inline definitions also bloat the parent document and blur the boundary the data model draws between a workflow and its reusable parts.

## Decision

A blueprint participates in workflow execution only by file reference: the sealed `Executable` hierarchy permits exactly `WorkflowDefinition`, `StepDefinition`, and `BlueprintRef`. An inline blueprint inside a `steps()` array is no longer representable — the type system, not a validator, rules it out.

The `BlueprintDefinition` type itself remains: it is the model for standalone `.blueprint.json` files, which `BlueprintRef` resolves. The removal is of inline *execution*, not of the blueprint concept.

## Alternatives considered

- **Keep both forms, prefer references by convention.** The dual loading/validation paths remain, and convention-only rules erode.
- **Deprecate inline, remove at 1.0.** Rejected under the pre-1.0 clean-break policy (ADR-0013): carrying a to-be-removed representation into the first release creates exactly the accidental API that policy exists to prevent.
- **Remove `BlueprintDefinition` entirely and inline-expand files at load.** Loses the blueprint as an addressable, individually validatable artifact.

## Consequences

### Positive

- One representation, one loading path, one validation path for reusable sequences.
- Reusable means reusable: every blueprint is a standalone, addressable file that any workflow can reference.
- The sealed permit makes the rule structural — no schema check or reviewer vigilance required.

### Negative

- A one-off sequence used by a single workflow must still be a separate file if it is to be a blueprint at all; small compositions pay a file-management cost.
- Existing definitions with inline blueprints required migration (performed in-change, per the pre-1.0 policy).

### Neutral / tradeoffs

- The sealed hierarchy centralizes the decision of what is executable; adding a new executable form is deliberately a core design change, not an extension point.

## Compatibility impact

Workflow definition schema: inline blueprint objects inside step arrays are invalid; `BlueprintRef` is the only in-workflow mechanism. Standalone blueprint files are unaffected. Public API: the sealed `Executable` permit is narrower than before — a deliberate pre-1.0 break.

## Implementation notes

`core/workflow/Executable.java` permits exactly `WorkflowDefinition`, `StepDefinition`, `BlueprintRef`; `BlueprintDefinition.java` remains as the standalone-file model. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

None.

## Related documents

- ADR-0001 (workflows as data — the representation discipline this enforces).
- ADR-0013 (the clean-break policy under which the removal was made).

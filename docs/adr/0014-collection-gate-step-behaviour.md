# ADR-0014: Collection Gate Step Behaviour

## Status

Proposed

## Date

2026-07-10 (proposal date)

## Context

The existing human gates (approval, input, review, step approval, tool approval) are single-response: one actor answers once and the run continues. Real workflows need a gate where a run pauses while *multiple* contributions are gathered — several actors submitting, replacing, or withdrawing items over time — before the workflow proceeds over the collected set.

Constraints and forces:

- Submissions must be schema-validated at the gate, not downstream.
- The full gate lifecycle (open, submit, replace, withdraw, close request, rejection, close, reopen) must be auditable through the runtime event contract.
- The framework has no identity model; deciding *who* may submit or close is an embedder concern and must live behind an SPI.
- The domain model for the gate already merged ahead of the runtime handler and schema surface. A half-landed surface (events and state types present, behaviour absent from the schema and runtime) must not ship in a release: complete it or cut it. The direction decision has been made to complete it.

## Decision

Add **COLLECTION** as a step behaviour: the run pauses in a collection-open state; actors submit, replace, or withdraw schema-validated items; the gate closes on an authorized close (with reopen supported), and the collected items become workflow context for subsequent steps.

- Authorization for per-actor gate actions is delegated to an embedder-supplied **authorizer SPI**; the framework ships no identity logic.
- Every lifecycle transition emits a dedicated audit event, keeping the gate replayable and verifiable like all other run behaviour.
- Testkit gains scripted gate support so collection scenarios run deterministically offline.

## Alternatives considered

1. **Loop of INPUT gates**: expressible today, but each iteration is a separate single-actor pause — no replace/withdraw semantics, no gate-level validation, and a noisy audit trail.
2. **External aggregation** (collect outside, feed one INPUT): pushes the interesting lifecycle outside the audit boundary, defeating the evidence guarantee.
3. **Cut the surface**: remove the merged domain model and events before release. Rejected by decision, retained here as the fallback if completion cannot be verified in time.

## Consequences

### Positive

- Multi-party contribution becomes a first-class, audited, replayable gate.
- Gate lifecycle events extend the verification surface naturally.
- Authorization stays cleanly outside the framework via the SPI.

### Negative

- The largest gate surface so far: more states, more events, more schema.

### Neutral / tradeoffs

- A new sealed behaviour permit and new event types are permanent vocabulary — acceptable pre-1.0 (ADR-0013), permanent after.

## Open questions

- The per-actor authorization descriptor model (rule-based resolution of who may perform which gate action) is an unresolved external decision; the SPI seam is designed to accommodate it without change. The shipped default authorizer denies every guarded action fail-closed, by design, until an embedder wires a richer implementation.

## Compatibility impact

- **API**: new behaviour permit; authorizer SPI (additive).
- **Runtime behavior**: new pause state and lifecycle; existing gates unchanged.
- **Workflow definitions**: new step type with item schema and close policy (additive, schema-versioned).
- **Configuration**: none required; absent authorizer fails closed.
- **Docs/examples**: gate documentation and one runnable multi-submission example.
- **Users**: opt-in; no effect on existing definitions.

## Verification note

Becomes Accepted once the root pull request targeting `main` (#19) has merged, bringing the schema, runtime handler, authorizer SPI, and the full lifecycle — open/submit/replace/withdraw/close-request/rejection/close/reopen, including deadline-close — onto the default branch, with black-box verification covering the full lifecycle ordering through the event contract (ADR-0011). Two dependent pull requests have already merged into the stack; only #19 itself remains open.

## Follow-up work

If completion cannot be made coherent before release, cutting the merged domain model and its events (the discarded alternative above) is the documented fallback — it must never ship as a dead, half-wired surface.

## Related documents

- ADR-0003 — fail-closed tool governance (fail-closed default pattern)
- ADR-0011 — runtime event contract as the verification surface
- ADR-0013 — pre-1.0 compatibility policy
- `docs/adr/README.md` — index

# ADR-0019: Workflow requirements and step-transition enforcement

## Status

Accepted

## Date

2026-07-09 (retrospective record date — the two companion designs were approved and merged earlier in 2026; no single decision date is separately recorded)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction. It covers two designs that declare themselves companions — the requirements framework and step-transition enforcement — as one decision, because the enforcement gates are what requirement-governed steps exist to feed; splitting them would re-litigate their own integration.

## Context

Real workflows depend on facts that live outside the definition: configuration values, approvals of record, environment-specific inputs. A portable definition (ADR-0001) must be able to *declare* such requirements without embedding where they come from — resolution inevitably touches persistence, identity, and policy, all of which belong to the embedding application (ADR-0002). Separately, human oversight needs teeth at step granularity: a review or approval verb that the runtime does not enforce as a state transition is documentation, not governance.

## Decision

**Requirements.** Workflow definitions declare requirements; declarations carry an opaque default payload the core never interprets. The framework owns the declaration model, requirement references, load-time validation, and a `RequirementResolver` SPI. How requirements are actually resolved — processors, value storage, persistence, security policy around resolution — is entirely the embedding application's side of the SPI.

**Step-transition enforcement.** Human gates at step granularity are runtime-enforced state transitions, not conventions: a step awaiting review holds the run in a dedicated status, review is forward-only, and a step-approval rejection transitions the run to failed. Every gate action threads the opaque, embedder-supplied `actorId` into the audit stream.

## Alternatives considered

- **Resolution fully in-framework.** Requires the framework to own persistence and identity for resolved values — a direct violation of the core's agnosticism (ADR-0002).
- **Requirements as ordinary context values.** No declaration contract, no load-time validation, no way for tooling or reviewers to see what a workflow needs before running it.
- **Two independent features** (requirements without transition gates, or gates without declared requirements). The gates are the enforcement half of the same governance story; shipping either alone leaves the other side conventional.
- **Advisory review verbs** (recorded but not state-enforced). Cheaper, but a gate the runtime cannot hold is not a gate.

## Consequences

### Positive

- Definitions state their external needs explicitly and portably; load-time validation catches missing declarations before execution.
- Review and step-approval are real controls: the run cannot proceed past an unmet gate, and a rejection has defined, terminal semantics.
- Every gate decision is attributable in the audit stream via `actorId`, without the framework holding any identity model.

### Negative

- Requirements are inert without embedder work: an embedding application must implement resolution to use them at all.
- The opaque default payload means the core cannot validate requirement *values* — only declarations; value-level correctness is the resolver's problem.

### Neutral / tradeoffs

- Forward-only review and rejection-to-failed are deliberately strict; workflows wanting softer semantics must model them explicitly (e.g. a retry pivot) rather than getting leniency from the gate.

## Compatibility impact

Requirement declarations and references are additions to the public workflow definition schema. `RequirementResolver` is a public SPI. Two run statuses (awaiting review, awaiting step approval), the gate verbs, and their transition semantics are runtime and event contract. All additive at introduction; transition semantics are behavioural contract from here on.

## Implementation notes

Requirements: `core.workflow.requirement` (`RequirementResolver` SPI, `DefaultRequirementResolver`, `WorkflowRequirement`, `RequirementScope`, `ResolutionMode`, `ResolutionContext`), `RequirementResolutionException`, runtime `RequirementCheckpoint`. Transition enforcement: `AWAITING_REVIEW` / `AWAITING_STEP_APPROVAL` in `WorkflowStatus`, `StepApprovalDecision`, `StepRejectionFailure`, and the approval surface on `WorkflowRuntime`. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

None for the framework half; resolution implementations are embedder territory by design.

## Related documents

- ADR-0001 (portable definitions the declarations extend).
- ADR-0002 (the boundary that puts resolution on the embedder's side).

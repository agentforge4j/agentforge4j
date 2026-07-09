# ADR-0002: The framework core carries no identity or commercial concepts

## Status

Accepted

## Date

2026-05-31

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction. The boundary decision was locked on 2026-05-31 and implemented before this record was authored.

## Context

The framework must be embeddable in any application and any deployment shape — a batch job, a service, a desktop tool, a hosted product. Identity, authorization, account models, and commercial concerns differ per embedder and per deployment; baking any one model into the framework would force opinions onto every embedder and conflict with their existing stacks.

## Decision

The framework carries no identity model, no user or organizational account model, and no monetization concepts of any kind. Identity and authorization are the embedding application's responsibility.

Concretely: runtime events carry no identity; `actorId` is an opaque string supplied by the embedder wherever a human action is attributed. SPIs (execution interception, event log, repositories, tool policy) are the seams through which an embedding application attaches identity, authorization, and lifecycle policy. Nothing in the core is conditional on a deployment mode — capability differences between embedders are expressed by which modules and SPI implementations are present, never by runtime flags.

## Alternatives considered

- **Built-in identity/auth module.** Convenient for demos, but forces an opinionated identity model, bloats the dependency surface, and collides with whatever the embedder already runs.
- **Optional identity behind configuration flags.** Produces dual code paths and flag-conditional behavior in the core — harder to test, easy to leak assumptions across the boundary.
- **Minimal user concept (id + roles) in core.** Even a minimal model encodes authorization semantics the framework cannot know; the opaque `actorId` gives attribution without semantics.
- **Softer boundary ("agnostic by default, identity-aware if configured").** Rejected: any identity vocabulary on core surfaces becomes a de-facto contract.

## Consequences

### Positive

- The framework embeds anywhere without adapters fighting an in-built model.
- The core's review surface stays small; the project carries no security liability for authentication code.
- Structural checks can enforce the boundary mechanically (vocabulary and dependency sweeps).

### Negative

- Every embedder must supply its own identity wiring; there is no batteries-included login path.
- Examples and tests must supply synthetic actor identifiers.

### Neutral / tradeoffs

- Audit attribution quality is only as good as the embedder's `actorId` discipline — the framework guarantees the field is threaded, not that it is meaningful.

## Compatibility impact

The SPIs through which embedders attach identity and policy are public, semver-governed contracts. The opaque `actorId` threading is part of the runtime and event contract. No workflow-definition impact.

## Implementation notes

Enforced across `agentforge4j-core` and `agentforge4j-runtime`; opaque `actorId` threading is visible on human-gate operations and audit events. Independently confirmed by a structural boundary review of the full module tree (2026-07-04): no identity, account, or commercial vocabulary on core surfaces. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

None for the core boundary itself.

## Related documents

- ADR-0012 (pre-execution interception — the primary policy seam for embedders).
- Module and architecture documentation (dependency direction, SPI catalogue).

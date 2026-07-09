# ADR-0008: Model-tier abstraction (LITE / STANDARD / POWERFUL)

## Status

Accepted

## Date

2026-07-09 (retrospective record date — the tier model was designed and implemented earlier in 2026; no single decision date is separately recorded)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction.

## Context

Workflow definitions are meant to be portable and durable (ADR-0001), but concrete model identifiers are neither: providers rename, release, and retire models continuously. A workflow that pins `some-model-v3` in its definition ages badly and binds the definition to one provider. Authors also rarely mean a specific model — they mean a capability class: "cheap and fast", "default", "strongest available".

## Decision

Agents declare a model **tier** — `LITE`, `STANDARD`, or `POWERFUL` — and the runtime resolves the tier to a concrete model per configured provider at execution time. An explicit model pin, where present, wins over tier resolution. In the core workflow model, tier names travel as strings; the typed `ModelTier` enum lives in the LLM API layer — a deliberate layering choice keeping the core independent of the LLM abstraction.

Model releases thereby become configuration changes: remap the tier, touch no workflow.

## Alternatives considered

- **Concrete model ids in definitions.** Maximally precise, minimally durable; every provider model change is a workflow content change.
- **Free-form capability tags** (author-defined labels resolved by convention). Flexible but unportable — a bundle's labels mean nothing on another installation without shared convention; three fixed tiers are a portable vocabulary.
- **Many tiers / a numeric capability scale.** False precision; providers do not expose a comparable scale, and mapping burden grows with every level.

## Consequences

### Positive

- Definitions stay provider-neutral and survive model churn; the shipped catalog can declare tiers meaningfully for any installation.
- Cost/latency shaping is a per-installation mapping decision, made once, not per workflow.
- Pin-over-tier gives an escape hatch when a step genuinely requires one exact model.

### Negative

- Three tiers are coarse; installations with many models must collapse them into three buckets.
- Tier semantics are relative to the installation's mapping — "POWERFUL" is not the same model everywhere, by design, which can surprise authors expecting reproducibility across installations.

### Neutral / tradeoffs

- String-typed tiers in the core trade compile-time safety for layering cleanliness; validation happens at load and resolution time.
- The tier set may grow: an additional capability tier is in review, and automatic tier selection has been proposed but is not accepted. This ADR covers the shipped three-tier model; extensions would amend or supersede it.

## Compatibility impact

The tier vocabulary is part of the public workflow definition schema and agent configuration surface. Tier additions are additive schema changes; the pin-over-tier precedence rule is a documented runtime contract.

## Implementation notes

`ModelTier` (three members) in the LLM API module; pin-over-tier precedence in the runtime's model resolution (`AgentInvoker`); string-form tier names in core definitions. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Decide and, if accepted, document the additional tier currently in review as an amendment to this ADR.

## Related documents

- ADR-0001 (portable definitions — the durability requirement this serves).

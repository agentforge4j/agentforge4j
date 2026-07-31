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

**Updated 2026-08-01 (editorial — reflects current state, does not change the decision):** the vocabulary is now four tiers, not three. [ADR-0035](0035-premium-as-a-fourth-model-capability-tier.md) added `PREMIUM` above `POWERFUL`, is Accepted, and amends this record. It amends only the enumerated set. Everything decided here is untouched: agents declare a capability tier rather than a model id, the runtime resolves it per configured provider at execution time, an explicit pin still wins over tier resolution, tier names still travel as strings through the core, and the typed `ModelTier` enum still lives in the LLM API layer. This record stays `Accepted` — amendment is not supersession.

The three-tier wording is deliberately left as originally written wherever it appears in this record — in the Decision paragraph above, in Alternatives considered ("three fixed tiers are a portable vocabulary"), in Consequences → Negative ("Three tiers are coarse … three buckets") and in Neutral / tradeoffs ("the shipped three-tier model"). Those sentences record what was decided and known when this record was written, and ADR-0035 quotes two of them verbatim in its Context, so rewriting them would break those citations and obscure what this ADR originally decided. Read them as historical; this note is the current statement of the vocabulary. Implementation notes below is corrected in place instead, because it is a pointer to where the code lives rather than a record of the decision — a reader following it should find what is there now.

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
- This ADR covers the shipped three-tier model; the tier set may grow, and extensions would amend or supersede this record.

## Compatibility impact

The tier vocabulary is part of the public workflow definition schema and agent configuration surface. Tier additions are additive schema changes; the pin-over-tier precedence rule is a documented runtime contract.

## Implementation notes

`ModelTier` (four members) in the LLM API module; pin-over-tier precedence in the runtime's model resolution (`AgentInvoker`); string-form tier names in core definitions. Verified on `main @ 9ad289dd` (2026-07-09); the member count re-verified on `main @ 6133f285` (2026-08-01).

**Updated 2026-08-01 (editorial — reflects current state, does not change the decision):** this section said `ModelTier` had three members, which was accurate when `LITE`, `STANDARD` and `POWERFUL` were the whole vocabulary. ADR-0035 added `PREMIUM`. The count is corrected in place rather than annotated alone, because this section exists to point a reader at live code; the original verification stamp is kept and the re-verification of the count recorded beside it. Nothing else in this section changed.

## Follow-up work

- A fourth capability tier is Accepted ([ADR-0035](0035-premium-as-a-fourth-model-capability-tier.md)) and amends this record: the tier abstraction decided here is unchanged, and only the enumerated set grows. Automatic tier selection has been proposed and is not accepted.

## Related documents

- ADR-0001 (portable definitions — the durability requirement this serves).

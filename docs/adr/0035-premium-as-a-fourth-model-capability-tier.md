# ADR-0035: PREMIUM as a fourth model capability tier

## Status

Proposed

## Date

2026-07-28

## Context

ADR-0008 fixed the tier vocabulary at three levels — `LITE`, `STANDARD`, `POWERFUL` — and named the coarseness as a known cost: "installations with many models must collapse them into three buckets". It also anticipated this record, closing with "the tier set may grow, and extensions would amend or supersede this record."

The bucket that has since become genuinely overloaded is the top one. Several providers now ship two models that both belong above `STANDARD` but differ materially in cost and latency — a strong general model and a distinctly more expensive reasoning-heavy one. With three tiers, a workflow author who wants the second must either pin a concrete model, forfeiting the portability ADR-0008 exists to protect, or map `POWERFUL` to the expensive model installation-wide and pay for it on every review step.

The tier was first scoped as one increment inside [ADR-0016](0016-deterministic-token-efficiency-governance.md) (deterministic token-efficiency governance), whose stack has not reached the default branch. It does not belong there: nothing in token-efficiency governance depends on a fourth tier, and nothing about a fourth tier depends on that stack landing. This record takes ownership of the decision so it can be evaluated, land, and be verified on its own; ADR-0016 no longer carries it.

## Decision

Add `PREMIUM` as a fourth tier, above `POWERFUL` in the ordered capability vocabulary. The tier abstraction from ADR-0008 is otherwise unchanged: tiers remain capability labels resolved per provider at execution time, tier names still travel as strings through the core, the typed enum still lives in the LLM API layer, and an explicit model pin still wins over any tier.

`PREMIUM` carries no requirement that an installation own a distinct model for it. Every shipped provider default maps `PREMIUM` to that provider's strongest available model — the same string as `POWERFUL` where no higher-capability model is configured. Two tiers resolving to one model is a supported configuration, not a gap to be closed.

This amends ADR-0008 rather than superseding it: the decision recorded there — capability tiers instead of pinned model ids — stands unchanged, and only the enumerated set grows.

## Alternatives considered

- **Leave the vocabulary at three and let authors pin.** Preserves the record as written, but pushes authors onto the exact escape hatch ADR-0008 treats as a last resort, and does so for a case that is now common rather than exceptional.
- **Rename the existing top tier and add below it.** Any renaming breaks every existing bundle and shipped catalog entry declaring `POWERFUL`, for a purely cosmetic gain.
- **An open-ended numeric capability scale.** Rejected in ADR-0008 as false precision; nothing about a fourth named level changes that reasoning.
- **A separate "cost ceiling" axis orthogonal to tier.** A larger and genuinely different design — two knobs where authors currently reason about one. Not warranted by the immediate problem, and not foreclosed by this record.

## Consequences

### Positive

- The overloaded top bucket splits without disturbing any existing declaration; `POWERFUL` keeps resolving exactly as before.
- Installations owning a distinct high-capability model can expose it as a portable tier rather than as a pin.
- Shipped defaults mean the tier resolves on every built-in provider from day one, so a catalog entry declaring it is not installation-specific.

### Negative

- Four tiers is more vocabulary for an author to hold, and the boundary between `POWERFUL` and `PREMIUM` is a judgement call rather than a definition.
- Where the two tiers ship mapped to the same model, an author may reasonably expect a difference and observe none. This is documented in the shipped-defaults contract, not enforced.
- Every future tier addition touches five surfaces — the enum, the shipped defaults, the two canonical JSON schema enums, and the workflow builder's hand-maintained duplicate of the agent schema (the builder's copy of the workflow schema is generated, the agent one is not) — which is why the alignment between them will be test-enforced rather than left to review. That enforcement is not in place yet; it lands with the configuration-surface change, and the Verification note below gates this record on it. The per-tier prose that also names the vocabulary — the `modelTier` Javadoc on `AgentDefinition` and `StepDefinition`, the provider READMEs and the type diagrams — is review-enforced, not test-enforced, and stays that way.

### Neutral / tradeoffs

- The relativity ADR-0008 flagged deepens slightly: `PREMIUM` is no more the same model across installations than `POWERFUL` is, by the same deliberate design.
- Adding an enum constant is source- and binary-compatible for consumers, since nothing in the framework switches exhaustively over `ModelTier`.

## Open questions

- Whether four is where the vocabulary settles. Nothing here establishes a principle for when a fifth level would be justified, and the "many tiers" alternative stays rejected.
- Whether the shipped `PREMIUM` mappings should diverge from `POWERFUL` for the providers that do offer a distinct higher-capability model, rather than defaulting conservatively to the same string everywhere.

## Compatibility impact

Public contract, additive in both directions. The `modelTier` enums in `agent.schema.json` and `workflow.schema.json` will gain a member when the configuration surface lands — they are unchanged by the runtime/API change that introduces the tier, so until then `PREMIUM` is not a publicly supported value in agent or workflow JSON. That widening is additive: nothing previously valid becomes invalid, and no consumer that already rejected the unknown string `PREMIUM` changes behaviour. The workflow `schemaVersion` therefore stays at 1. `ModelTier` gains an enum constant, which is binary-compatible; a consumer that exhaustively switches over the enum without a default would need a new branch, and none exists in this repository.

## Verification note

Accepted once merged to the default branch with: `ModelTier.PREMIUM` present and covered by `ModelTierTest`; a shipped default for `PREMIUM` on every built-in provider, asserted per provider in `ConfigModelTierResolverTest`; both JSON schema tier enums accepting it, tied back to `ModelTier.values()` by `ModelTierSchemaAlignmentTest` so the surfaces cannot drift apart unnoticed; and the builder's duplicated agent schema pinned against the canonical one by `ModelTierSchemaContractTest`, so the fifth surface cannot be the one that is forgotten.

## Related documents

- [ADR-0008](0008-model-tier-abstraction-lite-standard-powerful.md) — the model-tier abstraction this record amends.
- [ADR-0016](0016-deterministic-token-efficiency-governance.md) — where this tier was first scoped, before being lifted out into this record.
- [ADR-0001](0001-portable-workflow-definitions-with-a-deterministic-execution-contract.md) — the portability contract that motivates tiers over pinned model ids.

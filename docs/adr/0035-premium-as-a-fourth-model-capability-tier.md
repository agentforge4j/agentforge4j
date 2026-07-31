# ADR-0035: PREMIUM as a fourth model capability tier

## Status

Accepted

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
- Every future tier addition touches five surfaces — the enum, the shipped defaults, the two canonical JSON schema enums, and the workflow builder's hand-maintained duplicate of the agent schema (the builder's copy of the workflow schema is generated, the agent one is not) — which is why the alignment between them is test-enforced rather than left to review. The per-tier prose that also names the vocabulary — the `modelTier` Javadoc on `AgentDefinition` and `StepDefinition`, the provider READMEs and the type diagrams — is review-enforced, not test-enforced, and stays that way.

  **Updated 2026-07-31 (editorial — reflects current state, does not change the decision):** as drafted, this bullet said the alignment enforcement "is not in place yet", that it would land with the configuration-surface change, and that a Verification note gated this record on it. All three have since resolved: the configuration surface landed, the enforcement exists, and promotion to Accepted removes the Verification note the sentence pointed at. The tense is corrected above and the enforcing tests are named under Implementation notes.

### Neutral / tradeoffs

- The relativity ADR-0008 flagged deepens slightly: `PREMIUM` is no more the same model across installations than `POWERFUL` is, by the same deliberate design.
- Adding an enum constant is source- and binary-compatible for consumers, since nothing in the framework switches exhaustively over `ModelTier`.

## Compatibility impact

Public contract, additive in both directions. The `modelTier` enums in `agent.schema.json` and `workflow.schema.json` have both gained the member: the runtime/API change that introduced the tier left them untouched, and the configuration-surface change that followed widened both, so `PREMIUM` is a publicly supported value in agent and workflow JSON. That widening is additive: nothing previously valid becomes invalid, and no consumer that already rejected the unknown string `PREMIUM` changes behaviour. The workflow `schemaVersion` therefore stays at 1. `ModelTier` gains an enum constant, which is binary-compatible; a consumer that exhaustively switches over the enum without a default would need a new branch, and none exists in this repository.

**Updated 2026-07-31 (editorial — reflects current state, does not change the decision):** as drafted, this paragraph said the two schema enums "will gain a member when the configuration surface lands" and that `PREMIUM` was "not a publicly supported value in agent or workflow JSON" until then. That surface has since landed, so the tense is corrected above; the Implementation notes name the tests that hold the schemas and the enum together.

## Implementation notes

`ModelTier` (four members) in the LLM API module, covered by `ModelTierTest`; `ShippedModelTierDefaults` in the LLM module, mapping `PREMIUM` for all nine built-in providers and asserted per provider by `ConfigModelTierResolverTest` and as a whole matrix by `ShippedModelTierDefaultsTest`; the `modelTier` enums in `agent.schema.json` and `workflow.schema.json` (schema module), tied back to `ModelTier.values()` by `ModelTierSchemaAlignmentTest` in the verification module so the surfaces cannot drift apart unnoticed; the workflow builder's duplicated agent schema pinned against the canonical one by `ModelTierSchemaContractTest`, so the fifth surface cannot be the one that is forgotten. Operator overrides bind through `agentforge4j.llm.model-tiers.<provider>.premium`. Verified on `main @ 6133f285` (2026-07-31).

## Follow-up work

- Whether four is where the vocabulary settles. Nothing here establishes a principle for when a fifth level would be justified, and the "many tiers" alternative stays rejected.
- Whether the shipped `PREMIUM` mappings should diverge from `POWERFUL` for the providers that do offer a distinct higher-capability model, rather than defaulting conservatively to the same string everywhere.

## Related documents

- [ADR-0008](0008-model-tier-abstraction-lite-standard-powerful.md) — the model-tier abstraction this record amends.
- [ADR-0016](0016-deterministic-token-efficiency-governance.md) — where this tier was first scoped, before being lifted out into this record.
- [ADR-0001](0001-portable-workflow-definitions-with-a-deterministic-execution-contract.md) — the portability contract that motivates tiers over pinned model ids.

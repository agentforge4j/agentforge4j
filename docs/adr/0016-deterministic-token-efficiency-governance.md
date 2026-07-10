# ADR-0016: Deterministic Token-Efficiency Governance

## Status

Proposed

## Date

2026-07-10 (proposal date)

## Context

Multi-step, multi-agent workflows accumulate context aggressively: every agent step inherits the full run context, there is no declared control over what a step receives or produces, and no evidence trail of what was sent. Token usage is observable per call but not governable per workflow.

Constraints and forces:

- Governance must be deterministic and declared in the workflow definition, never left to agent discretion.
- Mechanisms and evidence must be provider-neutral and work identically across all provider modules.
- The framework carries no cost or monetary concepts; efficiency is expressed in tokens and structure only.
- Prompt assembly already has a stable 3-layer byte-offset boundary model (`PromptLayerBoundaries`) with prompt-cache wiring; an earlier 6-layer concept was found to contradict the live model and was dropped — the 3-layer model is adopted unchanged.
- Token-estimation logic previously existed as duplicated private heuristics inside individual provider modules.
- A separate workstream owns context *scoping* (an INHERIT/SCOPED/FRESH boundary concept, field name `contextScope`); this design must compose with it, not compete.

## Proposed decision

Introduce opt-in, deterministic token-governance primitives:

- **`contextSelection`**: a step declares the token-efficient context subset it receives. Composition contract: `contextScope` (owned elsewhere) decides the allowed context *boundary*; `contextSelection` chooses the subset *within* that boundary and may never expand context beyond it.
- **Context packs and selectors**: named, loadable context groupings with declarative selection.
- **Token ledger**: per-run, per-step token evidence recorded in the audit event stream via a provider-neutral report shape.
- **Output contracts**: declared bounds on step output, validated deterministically.
- **`CompactBehaviour`**: a new sealed `StepBehaviour` permit for explicit, replayable compaction of designated context — never silent truncation.
- **`RequestContextCommand`**: a new sealed `LlmCommand` permit letting an agent request additional context through the governed path.
- **Shared `TokenEstimator` SPI** in the LLM API layer, with a default implementation extracted from the previously duplicated per-provider character-based heuristic (those providers now delegate). Provider modules may register better estimators via `ServiceLoader`. This SPI is the single estimation home; other consumers (including workflow execution estimation, ADR-0017) consume it rather than duplicating it.
- **`PREMIUM` model tier** added to the sealed `ModelTier` set with resolver and shipped-defaults wiring, as neutral capability vocabulary.

No changes to `PromptLayerBoundaries` or prompt-layer structure.

## Alternatives under consideration

1. **Provider prompt caching only**: zero new surface, but caching reduces the cost of repetition, not context size, and provides no governance or evidence.
2. **Agent-side self-management** (instructing agents to be concise): non-deterministic and unauditable — conflicts with the engine-controls-flow principle.
3. **Selection only**: land `contextSelection` and defer ledger/compaction/output contracts. Rejected as the primary path — the mechanisms reinforce each other and the evidence trail is a core part of the value.

## Expected consequences

### Positive

- Workflow authors gain deterministic, declared control over per-step context and output.
- Token evidence lands in the same replayable audit stream as all other run evidence.
- Estimation is unified behind one SPI, removing per-provider duplication.
- Directly benefits large multi-bundle catalog workflows.

### Negative

- Substantial new schema vocabulary that becomes permanent once released.
- Compaction output is deterministic by record, not by recomputation — replay must read the recorded result.

### Neutral / tradeoffs

- Opt-in: existing definitions keep inherit-everything behaviour; gains require author action.
- Two new sealed-type permits enlarge the behaviour and command sets — acceptable under the pre-1.0 compatibility policy (ADR-0013); dispatch uses handler maps, so no exhaustive-switch breakage exists.

## Open questions

- Output-contract validation architecture: resolve schema references eagerly at load time or defer resolution.
- Attribution shape of per-call summaries in the ledger (whether the emitting agent is identified on the summary record).
- Waste-detection reporting: requires a persisted run-state history section whose shape is not yet settled.

## Decision criteria

- **Accept** when the implementation is merged to `main` and black-box verification asserts selection, ledger, and compaction evidence through the runtime event contract (ADR-0011).
- **Revise** if verification shows the composition contract with `contextScope` cannot be enforced at load time.
- **Reject** individual mechanisms (not the whole) if a mechanism proves unverifiable through the event contract.

## Compatibility impact

- **API**: new `TokenEstimator` SPI (additive); two new sealed permits (`CompactBehaviour`, `RequestContextCommand`); `PREMIUM` added to `ModelTier`. Pre-1.0 clean-break policy applies (ADR-0013).
- **Runtime behavior**: unchanged unless a workflow opts in; compaction adds a replay-by-record path.
- **Workflow definitions**: additive, schema-versioned fields (`contextSelection`, output contracts, compact steps, context packs).
- **Configuration**: estimator registration via `ServiceLoader`; pack loading configuration.
- **Docs/examples**: author-facing vocabulary — needs a dedicated guide plus reference pages; tier documentation gains `PREMIUM` with capability-only wording.
- **Users**: no impact on existing definitions; incremental opt-in.

## Implementation outline

1. Estimator SPI + `PREMIUM` tier in the LLM API layer (foundation increment).
2. Core domain types, compaction behaviour, schema surface.
3. Context-pack loading, selection validation, estimator-backed ledger, pack selectors.
4. Black-box verification of the evidence trail against the event contract.

**Verification note:** all implementation increments (estimator SPI and `PREMIUM` tier, core domain types and `CompactBehaviour`, schema surface, context-pack loading, context-selection validation, the estimator-backed ledger, context-pack selectors) plus a prompt-cache-stability verification pass are complete and merged into each other on a single stacked branch — none of this has reached `main`. The root pull request targeting `main` (#16) remains open; every dependent pull request in the stack has merged into #16's branch rather than into `main` itself, so `main` currently carries none of this ADR's surface (no `PREMIUM` tier, no `TokenEstimator` SPI, no `CompactBehaviour`, no `RequestContextCommand`). Confirm #16 has merged before moving this ADR to Accepted; until then, Proposed is unambiguous, not a judgment call.

## Related documents

- ADR-0008 — model-tier abstraction
- ADR-0011 — runtime event contract as the verification surface
- ADR-0013 — pre-1.0 compatibility policy
- ADR-0017 — workflow execution estimation (estimation SPI consumer)
- `docs/adr/README.md` — index

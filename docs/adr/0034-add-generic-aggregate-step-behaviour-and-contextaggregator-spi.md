# ADR-0034: Add generic AGGREGATE step behaviour and ContextAggregator SPI

## Status

Proposed

## Date

2026-07-11 (proposal date)

## Context

The execution estimator's shipped workflow needed to disclose a complete computed estimate (recommendation, confidence, token envelope) before its approval pause, without delegating that computation to host-side Java — plug-and-play catalog workflows can't require bespoke Java per bundle. No existing step behaviour covers "run a deterministic computation over context and write structured results back," distinct from validation (which is fail-closed, pass/fail) or agent steps (which are LLM-driven). `VALIDATE`'s prior addition of the `ArtifactValidator` SPI established the pattern for adding a new deterministic, ServiceLoader-discovered capability as a sealed `StepBehaviour` variant rather than overloading an existing one.

## Decision

Add `AGGREGATE` as a new `StepBehaviour` sealed-hierarchy variant, with a `ContextAggregator` SPI (`core.spi.aggregation`) discovered via direct `ServiceLoader.load(ContextAggregator.class, …)` — no factory, since (unlike `ArtifactValidatorFactory`) no aggregator needs a construction-time dependency. A read-only `AggregationContext` facade exposes context values to aggregators. `AggregateBehaviourHandler` writes the aggregator's flat, unprefixed logical field names back into context under the step's configured prefix.

## Alternatives considered

- **Host-side Java aggregation (status quo).** Rejected — violates the plug-and-play catalog requirement, forces bespoke Java per shipped workflow.
- **Model aggregation as a `VALIDATE` variant.** Rejected — conflates fail-closed validation semantics with non-failing computed disclosure; different failure/success contracts.

## Consequences

### Positive

- Enables deterministic, plug-and-play disclosure computation in catalog workflows with zero bespoke Java per bundle — consistent with the `VALIDATE`/`ArtifactValidator` precedent.
- Adds a new public SPI surface (`ContextAggregator`) requiring its own versioning/documentation discipline going forward.

### Negative

- Extends the sealed `StepBehaviour` hierarchy; future variants must re-verify (as this one did) that no exhaustive `switch` over the type exists before assuming safe default-dispatch via `instanceof`-chains.

### Neutral / tradeoffs

- `ContextAggregator` implementations are expected to be deterministic and side-effect free by documented convention only, not runtime-enforced — mirroring `ArtifactValidator`'s own unenforced purity expectation.

## Open questions

- Whether `ContextAggregator` purity (deterministic, side-effect free) should eventually be enforced (for example via a dedicated test-only checker) rather than left as documented convention.

## Compatibility impact

- **API**: new `core.spi.aggregation` package (`ContextAggregator`, `AggregationContext`) — additive public SPI. New `AggregateBehaviour` record joins the sealed `StepBehaviour` hierarchy (additive; exhaustive consumers of the sealed type must be extended, verified done for all existing ones). New `AgentForge4jBootstrap.Builder.withContextAggregators(...)` embedder hook, mirroring `withArtifactValidators(...)`.
- **Workflow definitions**: new `"type": "AGGREGATE"` step-behaviour discriminator — additive to the workflow JSON schema.
- **Runtime behaviour**: none for existing step types at the engine/behaviour-type level; purely
  additive. The shipped `workflow-execution-estimator` bundle itself does change: its `estimate`
  step's transition moves from `HUMAN_APPROVAL` to `AUTO`, and the new `aggregate-estimate` step
  becomes the run's approval-pause point instead — a real, user-visible change to that one bundle's
  pause semantics, not to the engine.
- **Configuration**: none beyond `ContextAggregator` ServiceLoader registration (module-path `provides` and classpath-mode `META-INF/services`, both required — module-path-only registration is invisible to classpath-mode test/embedding execution).

## Verification note

Becomes Accepted once this PR merges to `main` with the `AGGREGATE` behaviour, the `ContextAggregator` SPI, and the built-in `workflow-execution-estimate` aggregator verified via the full reactor (`core`/`runtime`/`bootstrap`/`workflows-catalog`) and the shipped `workflow-execution-estimator` bundle's catalog scenarios passing.

## Related documents

- ADR-0001 — portable workflow definitions (the sealed `StepBehaviour` hierarchy this extends)
- ADR-0009 — ServiceLoader discovery and JPMS module policy (the discovery mechanism this follows)
- ADR-0014 — collection gate step behaviour (the closest prior precedent: a new sealed `StepBehaviour` variant, Proposed the same way)
- ADR-0017 — workflow execution estimation (the motivating consumer feature)
- `docs/adr/README.md` — index

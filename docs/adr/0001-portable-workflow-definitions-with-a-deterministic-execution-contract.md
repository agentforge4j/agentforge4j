# ADR-0001: Portable workflow definitions with a deterministic execution contract

## Status

Accepted

## Date

2026-07-09 (retrospective record date — the decision has been in force since the framework's first design and predates formal recording)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction. It records the foundational execution-model decision after the fact; it was not authored before implementation.

## Context

LLM output is non-deterministic. Organizations embedding AI workflows need the opposite properties from the surrounding machinery: reproducibility, auditability, reviewability, and human oversight. Frameworks that express agent behavior as application code make workflows non-portable, hard to review, and hard to govern — the control flow lives wherever the code lives, and changing a workflow means a code change and redeployment. Frameworks that let the model plan its own control flow make execution unexplainable and unrepeatable.

The forces: a complete audit trail per run, replayable execution, human approval gates as first-class constructs, and a hard separation between what the model decides and what the engine decides.

## Decision

Workflows are data, not code; agents are configuration, not classes; every run is replayable and explainable.

Concretely: workflow definitions are portable JSON/markdown documents declaring steps, branching, retries, human gates, and tool access. A deterministic runtime executes them: LLMs decide *content*; the engine decides *flow*. Every run emits a complete audit event stream. The workflow decides, not the model.

## Alternatives considered

- **Code-first workflow DSL (fluent Java API).** Better IDE support and type safety, but workflows become non-portable compiled artifacts, hostile to review by non-developers, and the flow/content boundary blurs into application code.
- **Autonomous planning loop (the model decides the next step).** Maximally flexible, but non-deterministic, non-replayable, and unauditable — exactly the properties the target audience cannot accept.
- **Reusing a general-purpose BPMN engine.** Mature execution semantics, but poorly matched to LLM step semantics (commands, gates, sparring, tool governance) and forces a heavyweight modeling layer onto every workflow author.

## Consequences

### Positive

- Runs are replayable and explainable; the audit event stream is a verifiable contract.
- Definitions are diffable, reviewable, and versionable like any other data artifact.
- Builders and visualizers operate on the same data model as the engine.
- Black-box verification can assert runtime behavior against the event contract rather than implementation internals.

### Negative

- Data has an expressiveness ceiling relative to code; complex logic must decompose into step, branch, and loop semantics.
- The workflow JSON schema becomes a public compatibility surface that must be governed.

### Neutral / tradeoffs

- Business logic is kept out of agents by convention: the LLM classifies, deterministic branching routes. This is a discipline the schema encourages but cannot fully enforce.

## Compatibility impact

The workflow definition schema, the step-behaviour set, the run-status set, and the audit event types are all public contracts. Additions are additive; the schema is the primary long-term compatibility surface for users and tooling.

## Implementation notes

Implemented in `agentforge4j-core` (workflow/step/event model), `agentforge4j-runtime` (deterministic engine), `agentforge4j-schema` (JSON schema), and `agentforge4j-config-loader` (loading/validation). The event contract is exercised by the black-box verification suite (see ADR-0011). Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- A `schemaVersion` field on the wire format is an open prerequisite for external embedding surfaces.

## Related documents

- Workflow schema module (`agentforge4j-schema`) and the project documentation's positioning and concepts pages.

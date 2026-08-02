# ADR-0012: Pre-execution interception and blocked-run resumability

## Status

Accepted

## Date

2026-07-09 (retrospective record date — the interception seam and the resumability guarantee were designed and shipped in separate earlier rounds; no single decision date exists)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction, consolidating two companion decisions: the pre-execution interception SPI and the guarantee that an intercepted run remains resumable.

## Context

Embedding applications routinely need a policy gate *before* a run does any work: capacity limits, quotas, compliance holds, operational pauses. The framework itself cannot know these policies (ADR-0002), but without a first-class seam every embedder would improvise one — wrapping the run API externally, which misses internal resume paths and produces no uniform audit trail. Equally important is what a veto *means*: a run stopped by policy has not failed; treating it as failed destroys legitimate work-in-progress and conflates operational state with workflow outcome.

## Decision

1. **Pre-execution interception SPI.** A `RunExecutionInterceptor` is consulted before execution proceeds; it can veto (or size) the run. It is the single, uniform pre-execution policy seam — covering initial starts and resume paths alike.
2. **Blocked, never failed.** A vetoed run is recorded as blocked and persists in a paused, resumable state. It is never transitioned to a failed terminal state, and its state is persisted on every veto path — no work or context is lost. When the embedder's policy later permits, the run resumes where it stopped.

## Alternatives considered

- **Post-hoc cancellation** (start, then kill when policy says no). Work has already run — side effects, cost, partial state; a gate that fires after the gated action is not a gate.
- **Veto maps to FAILED.** Simple state model, but semantically wrong: a policy hold is not a workflow failure, and a terminal state forfeits the run instead of parking it.
- **Embedder-side wrapping of the run API.** No framework change needed, but blind to internal resume transitions, unenforceable as a single chokepoint, and invisible to the audit stream.

## Consequences

### Positive

- Embedders attach arbitrary pre-execution policy through one SPI, with vetoes visible in the audit event stream like every other run transition.
- Policy holds are recoverable by construction: lift the condition, resume the run.
- One seam covers all entry paths, so a policy cannot be bypassed via resume.

### Negative

- Blocked-but-resumable runs are long-lived state the embedder must surface and manage (a parked run is easy to forget without an operational view).
- The interceptor sits on the hot path of every run start and resume; a slow implementation slows everything.

### Neutral / tradeoffs

- The framework defines the mechanism and the state semantics; *why* a run is blocked is entirely the embedder's domain and is carried only as embedder-supplied detail.

## Compatibility impact

`RunExecutionInterceptor` is a public SPI and part of the extension contract. The blocked-run state semantics (paused, resumable, never failed, persisted on veto) are runtime contract, observable in run status and the event stream. Workflow definitions are unaffected.

## Implementation notes

`RunExecutionInterceptor` and `ExecutionBlockedException` in `runtime.interceptor`; the runtime records a blocked run and transitions it to the paused, resumable state — never the failed path — with state persisted on both veto paths (verified in `DefaultWorkflowRuntime`). Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

None for the OSS seam itself.

## Related documents

- ADR-0002 (why policy content lives with the embedder).
- ADR-0003 (the analogous chokepoint principle applied to tools).

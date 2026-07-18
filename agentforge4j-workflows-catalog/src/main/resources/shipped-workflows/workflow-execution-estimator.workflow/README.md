# Workflow Execution Estimator

Estimates the execution shape of a run **before** it executes: token range, agent turns, tool
invocations, complexity, confidence inputs, structural risk flags, and a continue/narrow/stop
recommendation. Two modes converge on the same structural-summary input shape, so the workflow's
own routing is mode-agnostic:

- **Mode 1 — workflow run**: estimate a single workflow run from its pre-analyzed structure.
- **Mode 2 — SDLC epic package**: estimate an epic-package breakdown the same way.

The caller resolves the mode and pre-analyzes the target deterministically before starting this
run, supplying the result as structural facts through the `structural-summary` input artifact.

## Steps

1. `collect-analysis` (INPUT) — collects the structural analysis facts.
2. `route-on-ceiling` (BRANCH) — routes on `ceilingDerivable`; `false` reaches `no-ceiling`
   (FAIL): work without a derivable finite execution ceiling is refused, fail-closed.
3. `estimate` (AGENT, `execution-estimator`) — sizes per-turn token and tool usage.
4. `aggregate-estimate` (AGGREGATE, HUMAN_APPROVAL) — combines the structural facts and the
   agent's sizing into the `executionEstimate.*` context keys and gates on human approval.

Execution shape only — the estimate is expressed in tokens and counts; there is no monetary
concept (OSS layer).

## Verification

Seven scenarios under `verification/` drive the real workflow through the testkit harness:
bounded positive/high-risk/epic-package runs paused at the approval gate, their approved
continuations to completion, and the fail-closed no-finite-ceiling rejection.

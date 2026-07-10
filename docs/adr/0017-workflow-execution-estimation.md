# ADR-0017: Workflow Execution Estimation

## Status

Proposed

## Date

2026-07-10 (proposal date)

## Context

Before running a large workflow — especially deep multi-bundle compositions — users need to understand its execution shape: how many steps and agent calls it can produce, how branches and loops bound that range, and roughly what token magnitude to expect. Today the only way to learn a workflow's shape is to run it.

Constraints and forces:

- Estimation must be deterministic where the definition is deterministic: structural analysis derives from the definition itself, not from a model's guess.
- The framework expresses effort in tokens and structure only — no monetary or cost framing anywhere in the estimation surface.
- Branch and loop constructs make execution a *range*, not a number; min/max semantics must be correct. Turn-counting for SPAR/EVALUATOR steps was found to undercount and has since been fixed. Branch-arm aggregation sums every reachable arm's turns into the range rather than selecting one — a documented, deliberate upper-bound approximation, not a defect, but still an open design question about whether that is the right semantics for deeply-nested branches.
- Token estimation must not be reinvented here: the shared `TokenEstimator` SPI (ADR-0016) is the single estimation home.

## Proposed decision

Two cooperating parts:

- **Framework utilities**: a deterministic complexity analyzer and execution aggregator in core, behind a facade — walking a workflow definition to produce a structural estimate (step counts, branch/loop bounds, token-magnitude hints via the shared estimator SPI).
- **A shipped estimator workflow** in the catalog: runs the deterministic analysis, then uses a lightweight agent step to classify the result into an actionable verdict — continue, narrow scope, or stop — before the target workflow is executed. The agent classifies; deterministic steps route (the determinism boundary holds).

Estimation output is advisory evidence, surfaced as context and audit events — it never gates execution by itself.

## Alternatives under consideration

1. **Dry-run execution** against the deterministic fake provider: accurate for one scripted path, but explores a single trajectory rather than the definition's full range, and costs a full run.
2. **Static docs guidance only** (author-annotated expectations): zero code, but unverifiable and immediately stale.
3. **Analyzer utilities only, no shipped workflow**: smaller surface, but loses the demonstrable end-user verdict flow that motivates the feature.

## Expected consequences

### Positive

- Users can judge a workflow's shape before committing to a run.
- The analyzer becomes reusable framework capability for any consumer needing structural workflow analysis.
- Demonstrates the framework's own pattern: deterministic analysis, LLM classification, engine routing.

### Negative

- Range semantics are hard to get right; wrong min/max numbers are worse than none. The turn-undercounting defect is fixed; branch-arm summing remains a documented approximation whose fitness for deeply-nested branches is still an open design question, not yet a defect to fix.
- Estimates can be mistaken for guarantees; documentation must frame them as structural bounds plus heuristics.

### Neutral / tradeoffs

- Splitting structural analysis (deterministic) from token magnitude (heuristic via the SPI) keeps the honest/approximate line visible but doubles the explanation burden.

## Open questions

- Whether branch-arm summing is the right upper-bound semantics for deeply-nested branches, or whether a different aggregation shape is needed.
- How estimates integrate with downstream consumers: context values only, dedicated events, or both.
- Whether the estimator workflow gains a mode tailored to multi-bundle compositions or stays generic.

## Decision criteria

- **Accept** when the analyzer's results are verified against known workflow fixtures and the shipped workflow passes catalog verification.
- **Revise** if the branch-arm summing question above shows the aggregate model needs a different shape than the current utilities expose.
- **Reject** the shipped-workflow half (keep utilities, alternative 3) if the verdict flow cannot be made deterministic-by-record.

## Compatibility impact

- **API**: analyzer/aggregator facade in core (additive); consumes ADR-0016's estimator SPI.
- **Runtime behavior**: none — estimation is advisory and pre-run.
- **Workflow definitions**: none required; the estimator workflow itself is catalog content.
- **Configuration**: none beyond estimator SPI registration.
- **Docs/examples**: estimator guide with explicit bounds-vs-heuristics framing; a runnable example.
- **Users**: opt-in; no effect on existing runs.

## Implementation outline

1. Core analyzer/aggregator utilities behind the facade (initial increment landed).
2. Resolve range semantics; correct the aggregation model.
3. Shipped estimator workflow (agent + bundle) with catalog verification scenarios.
4. Runnable example; documentation.

**Verification note:** the core analyzer/aggregator utilities have landed on `main`, including the turn-counting fix. The shipped estimator workflow itself remains unmerged: an open pull request carries it, targeting `main`. Confirm that pull request has merged, and that catalog verification passes, before moving this ADR to Accepted.

## Related documents

- ADR-0006 — code-free catalog artifact (the shipped workflow follows its rules)
- ADR-0011 — runtime event contract as the verification surface
- ADR-0016 — deterministic token-efficiency governance (estimator SPI owner)
- `docs/adr/README.md` — index

# Positive Bounded

Estimates a small, bounded target workflow (a "baby AI Agent birth" profile — seven steps: one
input step and six agent steps, the last of which carries a human-review gate; no loops or
branches). The caller supplies the precomputed structural facts as the run's input — one typed
field per fact, sourced directly from the target's structural analysis, no independently-supplied
JSON summary to duplicate or contradict them; the `execution-estimator` agent sizes the per-turn
token and tool magnitudes; the `aggregate-estimate` step then combines both in-workflow into the
full 9-field `executionEstimate.*` disclosure (`recommendation`, `confidence`, `complexity`,
`riskFlags`, `minimumRequiredTokens`, `estimatedMinTokens`, `estimatedExpectedTokens`,
`estimatedMaxTokens`, `iterationCeiling`) before the run pauses for human approval.

Expected: the run reaches `AWAITING_STEP_APPROVAL` (not `COMPLETED` — this proves the disclosure
fields are visible to the caller before any approve/reject decision is made, not merely after)
with all 9 `executionEstimate.*` fields present. `MODERATE` complexity (six agent steps crosses the
moderate threshold), a finite ceiling, and no risk flags.

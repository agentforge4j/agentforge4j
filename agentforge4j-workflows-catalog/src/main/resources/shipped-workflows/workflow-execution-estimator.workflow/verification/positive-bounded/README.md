# Positive Bounded

Estimates a small, bounded target workflow (a "baby AI Agent birth" profile — five agent steps,
one human review, no loops or branches). The caller supplies the precomputed structural analysis
as the run's input; the `execution-estimator` agent sizes the per-turn token and tool magnitudes;
the run pauses for human approval with every mandatory disclosure field present in context —
`complexity`, `ceilingDerivable`, `minimumRequiredTokens`, and the sized per-turn figures.

Expected: the run reaches `AWAITING_STEP_APPROVAL` (not `COMPLETED` — this proves the disclosure
fields are visible to the caller before any approve/reject decision is made, not merely after).
`SIMPLE` complexity, a finite ceiling, and no risk flags.

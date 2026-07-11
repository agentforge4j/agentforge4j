# Positive Bounded

Estimates a small, bounded target workflow (a "baby AI Agent birth" profile — seven steps: one
input step and six agent steps, the last of which carries a human-review gate; no loops or
branches). The caller supplies the precomputed structural facts as the run's input — one typed
field per fact, sourced directly from the target's structural analysis, no independently-supplied
JSON summary to duplicate or contradict them; the `execution-estimator` agent sizes the per-turn
token and tool magnitudes; the run pauses for human approval with every mandatory disclosure field
present in context — `complexity`, `ceilingDerivable`, `minimumRequiredTokens`, and the sized
per-turn figures.

Expected: the run reaches `AWAITING_STEP_APPROVAL` (not `COMPLETED` — this proves the disclosure
fields are visible to the caller before any approve/reject decision is made, not merely after).
`MODERATE` complexity (six agent steps crosses the moderate threshold), a finite ceiling, and no
risk flags.

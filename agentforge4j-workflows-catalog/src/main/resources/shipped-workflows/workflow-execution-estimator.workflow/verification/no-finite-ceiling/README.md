# No Finite Ceiling

Estimates a target described as containing an agent-driven loop with no maximum iteration bound
and no derivable termination — a structural-summary input pre-flagged as having no derivable
execution ceiling (standing in for a real unbounded/agent-driven-loop analysis; the deterministic
analyzer's own live output always derives a finite ceiling for a valid single workflow, since
`maxIterations` is mandatory — this case matters for epic-package estimation and malformed input).

The run routes straight to the `FAIL` terminal without invoking the `execution-estimator` agent —
no sizing, no LLM spend for a workload with no finite ceiling. Expected: `FAILED` status, `RUN_FAILED`
emitted, the `estimate` step never visited, and the no-ceiling reason recorded in context. Not
blocked — this is a definite terminal, not a refusal of large-but-bounded work (see `positive-bounded`
and, in a later phase, the high-risk-but-bounded scenario for that distinction).

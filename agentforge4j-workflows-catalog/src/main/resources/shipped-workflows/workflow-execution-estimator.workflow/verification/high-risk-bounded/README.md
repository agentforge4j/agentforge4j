# High Risk Bounded

Estimates a single, large-but-bounded target: the full AI Agent Adoption Center application build
(Mode 1) — many agent steps, several branches, and a bounded rework loop with a high iteration
ceiling. A finite ceiling still exists (`ceilingDerivable` remains `true`); the workload is simply
large and uncertain, not unbounded.

Expected: routes past `route-on-ceiling` to `estimate` exactly as `positive-bounded` does — `HIGH_RISK`
classification and a high iteration ceiling never block a run, they only widen the envelope and lower
confidence downstream. The run reaches `AWAITING_STEP_APPROVAL` with the full disclosure present,
same as any other outcome — proving `HIGH_RISK` is advisory, never a refusal (distinct from
`no-finite-ceiling`, which is a genuine terminal).

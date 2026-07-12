# Fail closed — unknown complexity

Proves the deterministic `[4]` tier-resolution tree fails closed on an out-of-vocabulary
`complexity` value (`intricate`, not one of `simple`/`moderate`/`complex`): the inner
`resolve-by-complexity` BRANCH has `failOnUnmatched=true` and no default branch, so the run fails
rather than silently defaulting a tier.

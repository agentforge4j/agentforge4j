# Fail closed — unmatched sensitivity floor

Proves the outer `resolve-tier` BRANCH fails closed on an out-of-vocabulary `sensitivityFloor`
value (`WEIRD`, neither `SENSITIVE` nor `NONE`): it has no default branch and
`failOnUnmatched=true`, so the run fails — distinct from the inner complexity/risk matrix's
unmatched-branch case (`fail-unknown-complexity`), which fails one level deeper in the tree.

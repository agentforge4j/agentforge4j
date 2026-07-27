# Tier resolution — POWERFUL (sensitivity floor)

Proves the deterministic `[4]` tier-resolution tree's outer branch: a `SECURITY` sensitivity flag
sets `sensitivityFloor=SENSITIVE`, which resolves to `recommendedTier=POWERFUL` via rule
`SENSITIVITY_FLOOR` — bypassing the complexity/risk matrix entirely, even though
`complexity=simple`/`risk=low` alone would resolve to LITE. Suspends at the `[7]` approval gate.

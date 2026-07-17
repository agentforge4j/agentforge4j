# Fail closed — [9v] modelTier mismatch introduced after [8v]

Proves `[9v]` (`validate-final`) independently re-checks the `modelTier == recommendedTier`
equality contract, not just `[8v]`: `generate-agent` writes a correct `agent.json` (passing
`[8v]`), but `generate-verification`'s `CREATE_FILE` overwrites `agent.json` with a mismatched
`modelTier` (CREATE_FILE is last-write-wins) — `[9v]` catches it, failing before `review` ever
runs.

# Fail closed — [8v] modelTier mismatch

Proves `[8v]` (`validate-agent`)'s `modelTier == recommendedTier` equality contract fails closed:
`generate-agent` writes an `agent.json` whose `modelTier` does not match the deterministically
resolved `recommendedTier`, so validation fails before `generate-verification` ever runs.

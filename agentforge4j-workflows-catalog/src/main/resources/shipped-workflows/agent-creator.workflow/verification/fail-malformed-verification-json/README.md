# Fail closed — malformed verification/script.json

Proves `[9v]` (`validate-final`, the `agent-creator-bundle` validator) structurally validates the
generated verification starter, not just presence: `generate-verification` writes a
`verification/script.json` that is not valid JSON, so validation fails before `review` ever runs.

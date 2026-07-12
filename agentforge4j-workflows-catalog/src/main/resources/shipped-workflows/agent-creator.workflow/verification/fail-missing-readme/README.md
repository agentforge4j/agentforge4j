# Fail closed — [8v] missing required bundle file

Proves `[8v]` (`validate-agent`)'s `requiredArtifacts` allowlist fails closed on an incomplete
bundle: the generator omits `README.md`, so the required-artifact check rejects the bundle before
`generate-verification` ever runs.

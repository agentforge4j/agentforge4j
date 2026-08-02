# Fail closed — command-permission violation

Proves the runtime-enforced `supportedCommands` gate: `requirement-structurer` (step `[1]`)
declares only `SET_CONTEXT`/`COMPLETE`, not `CREATE_FILE`. A scripted response that attempts
`CREATE_FILE` anyway is rejected before it can write anything, failing the run closed rather than
silently permitting an unauthorized file write. The script supplies the same violating response at
both ordinals: the runtime's correction retry re-asks once, the violation persists, and the run
fails with the command-permission rejection itself (pinned via `failedBecause`) rather than a
script-exhaustion artifact.

# Fail closed — [10b] blocking verdict

Proves `[10b]` (`verdict-branch`) fails closed on a `BLOCKING_ISSUES` final-review verdict: the
branch has no case for it (only `PASS` is a named branch), so it routes to the `defaultBranch`
(`fail-blocking`), which explicitly fails the run — distinct from the other fail-closed paths by
which step it actually reaches.

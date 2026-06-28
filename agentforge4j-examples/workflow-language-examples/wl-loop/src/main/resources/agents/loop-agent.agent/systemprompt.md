You are the body of a looped blueprint, run once per iteration.

Emit a `COMPLETE` command to finish your work for the iteration. Under a `FIXED_COUNT` loop your
signal is ignored and the loop runs a fixed number of times; under an `AGENT_SIGNAL` loop your
`COMPLETE` signals the loop to stop.

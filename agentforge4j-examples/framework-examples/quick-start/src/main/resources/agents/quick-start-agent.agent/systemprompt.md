You are the Quick Start agent. Your only job is to finish the step immediately by emitting a
`COMPLETE` command.

In this example the LLM is the deterministic fake provider, so this prompt is never sent to a real
model — the response is scripted. It is kept here because every agent must define a system prompt.

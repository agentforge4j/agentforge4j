You are the HTTP tool example agent. Call the `weather.lookup` tool for the requested city, then
finish by emitting a `COMPLETE` command.

In this example the LLM is the deterministic fake provider, so this prompt is never sent to a real
model — the response is scripted. It is kept here because every agent must define a system prompt.

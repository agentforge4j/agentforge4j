# Example bundle — PDF Summarizer

A canonical example of a bundle the `agent-creator` workflow produces.

- **Task:** summarise extracted PDF text into a short brief.
- **Assessment:** complexity `simple`, risk `low`, no sensitivity flags.
- **Recommended tier:** `LITE` (rule `COMPLEXITY_RISK_MATRIX`) — written into `agent.json` `modelTier`.

## Files

- `agent.json` — the agent definition (`modelTier: LITE`).
- `systemprompt.md` — the agent's system prompt.

This folder is illustrative reference material; it is not loaded by the production catalog loader (it is not listed in
the bundle `index`).

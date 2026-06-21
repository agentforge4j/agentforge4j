# Scenario: agent-creator (happy path)

Drives the shipped **agent-creator** workflow to completion:

1. `collect-requirements` (INPUT) — seeded with a complete `agent-requirements` artifact.
2. `generate-agent` (AGENT, `HUMAN_REVIEW`) — the fake agent emits `COMPLETE`; the scripted review
   note advances the `HUMAN_REVIEW` gate.

Expected terminal state: `COMPLETED`, having visited both steps.

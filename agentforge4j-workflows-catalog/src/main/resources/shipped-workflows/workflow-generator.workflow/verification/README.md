# Scenario: workflow-generator (happy path)

Drives the shipped **workflow-generator** workflow to completion:

1. `start-conversation` (INPUT) — seeded with the `workflow-ideas` idea.
2. `interactive-design` (AGENT, `AUTO`) — the fake agent sets `workflow-design` then emits `COMPLETE`
   (rather than `USER_PROMPT`, which would pause for further conversation).
3. `generate-workflow` (AGENT, `AUTO`) — the fake agent emits `COMPLETE`.

Expected terminal state: `COMPLETED`, having visited all three steps.

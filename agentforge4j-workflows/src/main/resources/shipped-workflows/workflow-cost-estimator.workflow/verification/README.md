# Scenario: workflow-cost-estimator (happy path)

Drives the shipped **workflow-cost-estimator** workflow to completion:

1. `collect-input` (INPUT) — seeded with the required `estimation-input` items (workflow JSON +
   subscription tier).
2. `estimate-cost` (AGENT, `AUTO`) — the fake agent emits `COMPLETE`.

Expected terminal state: `COMPLETED`, having visited both steps.

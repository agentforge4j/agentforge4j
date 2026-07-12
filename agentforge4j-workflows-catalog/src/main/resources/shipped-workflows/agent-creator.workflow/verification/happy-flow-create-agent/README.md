# Agent Creator — Case-A verification scenario

This is the shipped, auto-discovered verification scenario for the `agent-creator` workflow. It is discovered by
`CatalogScenarios` and enforced by `CatalogConformanceTest`, and run end-to-end by `ShippedCatalogScenarioTest`.

## What it proves (happy path)

Driven by `script.json` (scripted fake-LLM responses) and the human gates in `expected-result.json`:

1. The run collects the agent idea (`collect-intent`).
2. It structures requirements, needs no clarification, and assesses the task as `simple` / `low` with no sensitivity.
3. Tier resolution deterministically yields `recommendedTier = LITE` (rule `COMPLEXITY_RISK_MATRIX`).
4. It previews the design and a token estimate, then **suspends at the approval gate** (`estimate-tokens`,
   `HUMAN_APPROVAL`).
5. On approval it generates the bundle (`agent.json`, `systemprompt.md`, `README.md`), which passes deterministic
   validation (including `agent.json` `modelTier == recommendedTier`), generates the verification starter, passes the
   final-bundle validation, and the quality review returns `PASS`, completing the run.

The pre-approval "no files written" assertion and the deterministic-fail cases live in the module's JUnit test sources
(they cannot share this single happy-path scenario — the harness drains all gates and asserts one final state).

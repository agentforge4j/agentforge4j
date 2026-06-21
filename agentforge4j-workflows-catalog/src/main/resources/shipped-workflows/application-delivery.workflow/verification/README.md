# Scenario: application-delivery (PO loop + per-epic sub-workflow FOR_EACH)

Drives the genuine shipped `application-delivery` workflow through the testkit harness with
deterministic fake-LLM responses and scripted human gates, to a `COMPLETED` run.

This scenario exercises the structures the workflow was previously held on — the `po-refinement-loop`
(`AGENT_SIGNAL`) and the `epic-loop-blueprint` (`FOR_EACH` over `epics`, each iteration invoking the
`epic-implementation` sub-workflow). The `AGENT_SIGNAL` body became drivable only once the clean-exit
defect (agentforge4j/agentforge4j#97) was fixed.

## Flow

1. `collect-app-idea` (INPUT) — supply the `appIdea` (`input` gate).
2. `po-refinement-loop` → `po-refinement` (`AGENT_SIGNAL` loop body) — the PO agent emits `COMPLETE`,
   so the loop terminates by signal after one iteration, leaving a `productVision`.
3. `approve-product-vision` (AGENT, `HUMAN_APPROVAL`) — approve (`escalation` gate).
4. `generate-epics` (AGENT, `HUMAN_APPROVAL`) — the BA agent emits a single-element `epics` list;
   approve (`escalation` gate).
5. `design-architecture` (AGENT, `HUMAN_APPROVAL`) — produce the architecture; approve (`escalation`
   gate).
6. `epic-loop-blueprint` → `invoke-epic-implementation` (`FOR_EACH` over `epics`) — one epic, so the
   `epic-implementation` sub-workflow runs once and drives its own understand/implement/test/validate
   steps to `epicStatus = SUCCESS` via fake responses keyed by the nested `epic-implementation`
   workflow id.
7. `assemble-delivery` → `executive-summary` (AGENT, AUTO) — assemble and summarise; the run completes.

## Asserted

- Final status `COMPLETED`.
- Every top-level step plus the loop body (`po-refinement`) and the per-epic sub-workflow step
  (`invoke-epic-implementation`) were visited.
- `epicStatus` context equals `SUCCESS` (the nested sub-workflow ran and succeeded).

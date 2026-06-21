# Scenario: app-creator (end-to-end SDLC with loop + SPAR + approval gates)

Drives the genuine shipped `app-creator` workflow through the testkit harness with deterministic
fake-LLM responses and scripted human gates, to a `COMPLETED` run.

This scenario exercises the structures the workflow was previously held on — the
`po-refinement-loop` (`AGENT_SIGNAL`) and the `dev-architect-spar` (Developer challenges Architect) —
both of which only became drivable once the `AGENT_SIGNAL` clean-exit defect
(agentforge4j/agentforge4j#97) was fixed.

## Flow

1. `collect-idea` (INPUT) — supply the `appIdea` (`input` gate).
2. `po-refinement-loop` → `po-refine` (`AGENT_SIGNAL` loop body) — the PO agent emits `COMPLETE`, so
   the loop terminates by signal after one iteration.
3. `po-finalise` (AGENT, `HUMAN_APPROVAL`) — approve the product vision (`escalation` gate).
4. `ba-epics` (AGENT, `HUMAN_APPROVAL`) — approve the epics (`escalation` gate).
5. `architect-design` (AGENT) — produce the architecture.
6. `dev-architect-spar` → `dev-architect-review` (SPAR) — one exchange round (neither side requests
   another round), then the architect's resolution round produces the agreed `architectureDesign`.
7. `architect-confirm` (AGENT, `HUMAN_APPROVAL`) — approve the final architecture (`escalation` gate).
8. `dev-plan` → `tester-plan` → `assemble-package` (AGENT, AUTO) — plan, test strategy, and final
   package; the run completes.

## Asserted

- Final status `COMPLETED`.
- Every top-level step plus the loop body (`po-refine`) and the SPAR step (`dev-architect-review`)
  were visited.

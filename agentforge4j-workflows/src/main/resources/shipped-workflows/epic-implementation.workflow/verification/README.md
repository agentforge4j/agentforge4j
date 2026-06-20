# Scenario: epic-implementation (pins corrected branch-routing behaviour)

Drives the shipped **epic-implementation** workflow: `understand-epic` → `implement-epic` →
`generate-tests` → `validate-epic` (fake validator returns `epicStatus=SUCCESS`) → `rework-decision`
(BRANCH on `epicStatus`).

> **This scenario pins the corrected behaviour after the explicit-null branch-routing fix
> (agentforge4j/agentforge4j#101, issue #96). It is no longer a suspected-defect pin.**
>
> The validator reports `SUCCESS`, and the shipped workflow JSON maps `"SUCCESS": null`.
> `BranchBehaviourHandler` now routes a matched key with a null value as a matched-but-empty branch
> that completes without falling through to the `default` branch (`mark-epic-failed-blueprint`). The
> run therefore ends `COMPLETED` with `epicStatus=SUCCESS` and never visits `mark-epic-failed`.

Expected terminal state: `COMPLETED`, having visited the four agent steps (`understand-epic`,
`implement-epic`, `generate-tests`, `validate-epic`), **without** visiting `mark-epic-failed`, with
final `epicStatus=SUCCESS`.

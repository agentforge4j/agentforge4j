# Scenario: recruitment (intake + CV loops, confirmation branches, per-candidate assessment)

Drives the genuine shipped `recruitment` workflow through the testkit harness with deterministic
fake-LLM responses and scripted human gates, to a `COMPLETED` run.

This scenario exercises the structures the workflow was previously held on — the `intake-loop` and
`cv-collection-loop` (`AGENT_SIGNAL`), the three confirmation `BRANCH` gates, and the
`assessment-per-candidate` (`FOR_EACH` over `shortlistedCandidates`). The `AGENT_SIGNAL` bodies became
drivable only once the clean-exit defect (agentforge4j/agentforge4j#97) was fixed.

## Flow

1. `initial-role-input` (INPUT) — supply the `rolePrompt` (`input` gate).
2. `intake-loop` → `intake-iteration` (`AGENT_SIGNAL`) — the intake agent emits `COMPLETE`, ending the
   loop after one round with a `recruitmentProfileDraft`.
3. `profile-finalize` (AGENT) → `profile-confirmation` (INPUT, confirm) → `profile-confirmation-gate`
   (BRANCH) — confirmed, so the run continues.
4. `job-post-channel-input` (INPUT) → `job-post-generation` (AGENT) → `job-post-approval` (INPUT,
   approve) → `job-post-approval-gate` (BRANCH, default) → `job-post-publish-file` (AGENT).
5. `cv-collection-loop` → `cv-upload-prompt` (INPUT) → `cv-analysis` (AGENT) → `cv-loop-coordinator`
   (AGENT, emits `COMPLETE`) — one CV, loop ends.
6. `shortlist-config-input` (INPUT) → `candidate-ranking` (AGENT, emits a one-element
   `shortlistedCandidates` list) → `shortlist-confirmation` (INPUT, approve) →
   `shortlist-confirmation-gate` (BRANCH, default) → `rejection-letters-generation` (AGENT).
7. `assessment-per-candidate` (`FOR_EACH`) → `assessment-generation` (AGENT) →
   `assessment-submission-input` (INPUT) → `assessment-evaluation` (AGENT) — one candidate.
8. `final-selection` (AGENT, `HUMAN_REVIEW`) — review (`review` gate); the run completes.

## Asserted

- Final status `COMPLETED`.
- The intake/profile/job-post/ranking/final-selection steps were visited.

# Recruitment Workflow — AgentForge4j Bundle

End-to-end AI-assisted recruitment workflow demonstrating dynamic intake, structured candidate evaluation, role-specific assessment, and explainable shortlisting. Built entirely on AgentForge4j primitives with no extension points required.

---

## Bundle Contents

```
recruitment.workflow/
├── workflow.json                                # Top-level workflow definition
├── index                                        # Bundle index (loader manifest)
├── *.artifact.json                              # User input forms (8 artifacts)
├── *.blueprint.json                             # Reusable step sequences (3 blueprints)
└── STATE-SCHEMAS.md                             # Reference schemas for all context values

agents/
├── recruitment-intake-agent.agent/              # Dynamic question generator
├── recruitment-profile-finalizer-agent.agent/   # Profile normalisation
├── recruitment-job-post-agent.agent/            # Channel-aware job post writer
├── recruitment-job-post-publisher-agent.agent/  # File output
├── recruitment-cv-analysis-agent.agent/         # Per-CV scoring engine
├── recruitment-cv-loop-coordinator-agent.agent/ # CONTINUE/COMPLETE on user signal
├── recruitment-ranking-agent.agent/             # Multi-criteria ranking + rationale
├── recruitment-rejection-letter-agent.agent/    # Explainable rejection letters
├── recruitment-assessment-generator-agent.agent/# Role-specific assessment authoring
├── recruitment-assessment-evaluator-agent.agent/# Submission scoring
└── recruitment-final-selection-agent.agent/     # Hiring-team-facing final report
```

Each agent directory contains `agent.json`, `systemprompt.md`, and (for the intake agent) `boundaries.md`.

---

## High-Level Flow

```
1. INPUT: rolePrompt
2. BLUEPRINT (LOOP, AGENT_SIGNAL): intake-loop
     └─ recruitment-intake-agent — dynamic, never hardcoded questions
3. AGENT: recruitment-profile-finalizer-agent → recruitmentProfile
4. INPUT: profile-confirmation
5. BRANCH on profileConfirmed:
     false → FAIL
     default → continue
6. INPUT: jobPostChannel
7. AGENT (with retryPolicy): recruitment-job-post-agent → jobPost
8. INPUT: jobPostApproved
9. BRANCH on jobPostApproved:
     false → RETRY_PREVIOUS from step 7 (max 3, fallback FAIL)
     default → AGENT recruitment-job-post-publisher-agent (CREATE_FILE)
10. BLUEPRINT (LOOP, AGENT_SIGNAL): cv-collection-loop
      ├─ INPUT: cv-upload
      ├─ AGENT: recruitment-cv-analysis-agent → candidates[]
      └─ AGENT: recruitment-cv-loop-coordinator-agent → CONTINUE or COMPLETE
11. INPUT: shortlistSize
12. AGENT: recruitment-ranking-agent → shortlistedCandidates, rejectedCandidates
13. INPUT: shortlistConfirmed
14. BRANCH on shortlistConfirmed:
      false → RETRY_PREVIOUS from step 11 (max 3, fallback FAIL)
      default → AGENT recruitment-rejection-letter-agent (CREATE_FILE per rejected)
15. BLUEPRINT (LOOP, FOR_EACH over shortlistedCandidates): assessment-per-candidate
      ├─ AGENT: recruitment-assessment-generator-agent → currentAssessment + CREATE_FILE
      ├─ INPUT: assessment-submission
      └─ AGENT: recruitment-assessment-evaluator-agent → updates candidates[]
16. AGENT: recruitment-final-selection-agent → finalSelection + CREATE_FILE
```

---

## Design Decisions

### No hardcoded questions

The intake step is a `BLUEPRINT` containing a single `AGENT` step inside a `LOOP` with `AGENT_SIGNAL` termination. The agent decides each turn whether to ask another `USER_PROMPT` (and stop, letting the runtime pause for user response) or emit `COMPLETE` to exit the loop. The framework executes the loop deterministically — the agent only contributes the *content* of each iteration.

### Human-in-the-loop confirmation gates

Three explicit confirmation gates: profile, job post, shortlist. Each uses a `BOOLEAN` artifact item (`InputBehaviour`) followed by a `BranchBehaviour` keyed on the boolean. This is more robust than `HUMAN_APPROVAL` transition because:

- the user gives a single explicit boolean answer
- failure paths are first-class (`RETRY_PREVIOUS` for regenerable artefacts, `FAIL` for unrecoverable rejection)
- the branch values `"true"` and `"false"` map directly to `BooleanContextValue` rendering

### Resume-safety

Every step writes its outputs through `SetContextCommand`. Combined with `StepSequenceExecutor`'s skip-already-executed behaviour (per project knowledge), a paused or crashed run can be resumed cleanly — partial CV analyses, completed assessments, and the recruitment profile are all in `state.context` and `state.stepOutputs`.

### Loops

| Loop | Strategy | Rationale |
|---|---|---|
| `intake-loop` | `AGENT_SIGNAL` | Agent is the only one who knows when the profile is sufficient. |
| `cv-collection-loop` | `AGENT_SIGNAL` | Coordinator agent reads `moreCvs` from the user input artifact and emits `CONTINUE` or `COMPLETE`. Cleaner than wiring a branch step. |
| `assessment-per-candidate` | `FOR_EACH` | Iterate exactly over the shortlisted candidates. `forEachContextKey` is `shortlistedCandidates`; the runtime exposes `currentCandidate` per iteration. |

### Rejection paths

- Profile not confirmed → `FAIL` (intake must restart entirely; no partial credit)
- Job post not approved → `RETRY_PREVIOUS` from `job-post-generation` (channel input is preserved); fallback after 3 attempts is `FAIL`
- Shortlist not confirmed → `RETRY_PREVIOUS` from `shortlist-config-input` (lets user adjust shortlist size); fallback after 3 attempts is `FAIL`

### Provider preferences

- **Cheap model first** (gpt-4o-mini → claude-3-haiku → ollama/llama3.2): intake, profile finalizer, CV loop coordinator, job post publisher. These are routine, structured tasks where a small model is sufficient.
- **Capable model first** (gpt-4o → claude-3-5-sonnet → ollama/llama3.1:70b): job post generation, CV analysis, ranking, rejection letters, assessment generation/evaluation, final selection. These require nuanced reasoning, multi-criteria trade-offs, or high-quality writing.

### Explainable rejection

Every rejected candidate gets:

1. A structured `rejectionReason` object (in `rejectedCandidates`) with `summary`, `primaryGap`, `evidence[]`, `missingMustHaves[]`, `alignedAreas[]`.
2. A markdown rejection letter file written via `CREATE_FILE`, available for download from the run.

Vague phrases like "not a good fit" are explicitly forbidden in the rejection-letter agent's system prompt.

### Files produced by a run

A single workflow run produces these downloadable files (via `CreateFileCommand`):

- `job-post-<role>-<channel>-<date>.md` — approved job post
- `rejection-<candidateId>-<name>.md` — one per rejected candidate
- `assessment-<candidateId>-<function>.md` — one per shortlisted candidate (candidate-facing)
- `final-selection-<role>-<date>.md` — hiring-team-facing final report

All accessible via `GET /api/v1/runs/{runId}/files` once the file download endpoint is in place.

---

## Banking / Enterprise Demo Notes

- Intake agent's boundary file refuses protected-characteristic preferences (age, gender, marital status).
- `complianceRequirements` is a first-class profile field. Banking industry context triggers the intake agent to probe regulatory exposure explicitly.
- Compensation and rejection letters never contain other candidates' names or scores — every output is candidate-isolated.
- Every decision point produces structured rationale persisted in context — fully auditable through `GET /api/v1/runs/{runId}/events` and `GET /api/v1/runs/{runId}` (state).
- Scoring is deterministic and weighted; the formula is documented and stored alongside each candidate's `rationale.scoringExplanation`. No black-box "vibes" scoring.

---

## Loading

This bundle is structured to drop into either:

1. **Filesystem loader** — copy `recruitment.workflow/` to the configured `agentforge4j.agents-path`'s sibling workflows directory; copy each agent directory to the agents path. `FileSystemWorkflowLoader` and `FileSystemAgentLoader` will pick them up.

2. **Classpath loader (shipped workflow)** — to ship as a built-in workflow, place under `/shipped-workflows/recruitment.workflow/` in the `agentforge4j-workflows` resources, add `recruitment` to `/shipped-workflows/index`, and ensure the bundle `index` file is present (already is). Agent bundles go under `/shipped-agents/<agentId>.agent/` with the agent ids added to `/shipped-agents/index`. `ClasspathWorkflowLoaderTest` will then enforce bundle correctness.

---

## Validation Coverage

The workflow exercises every validation in `AgentForgeLoader`:

- `validateAgentRefs` — 11 distinct agent refs, all must resolve
- `validateBlueprintRefs` — 3 blueprints, all referenced; recursive walk into `BranchBehaviour.branches` and `defaultBranch` covers the gates
- `validateArtifactRefs` — 8 artifacts, all referenced by `InputBehaviour` steps
- `validateRetryStepRefs` — 2 `RetryPreviousBehaviour` instances, both referencing existing step ids
- `validateCircularRefs` — none (no nested workflows)
- `validateWorkflowRefs` — none (no `WorkflowBehaviour` steps)

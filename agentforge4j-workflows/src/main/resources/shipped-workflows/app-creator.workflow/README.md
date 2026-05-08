# App Creator Workflow Bundle

A complete AgentForge4j workflow that takes an application idea and produces a full delivery package via a multi-agent SDLC pipeline (PO → BA → Architect ↔ Developer → Tester → Assembler).

## Bundle Layout

```
app-creator-bundle/
├── app-creator.workflow/                  # Drop into agentforge4j-workflows resources
│   ├── workflow.json
│   ├── index                              # Bundle index — required for ClasspathWorkflowLoader
│   ├── app-idea.artifact.json
│   ├── po-refinement-loop.blueprint.json
│   ├── dev-architect-spar.blueprint.json
│   ├── po-finalise.step.prompt.md
│   ├── architect-confirm.step.prompt.md
│   └── assemble-package.step.prompt.md
├── agents/                                # Drop into shipped-agents OR a filesystem agents path
│   ├── app-creator-po-agent.agent/
│   ├── app-creator-ba-agent.agent/
│   ├── app-creator-architect-agent.agent/
│   ├── app-creator-developer-agent.agent/
│   ├── app-creator-tester-agent.agent/
│   └── app-creator-assembler-agent.agent/
└── EXAMPLE_CONTEXT_STRUCTURES.md          # Reference only — not loaded
```

## Workflow Shape

```
[1] collect-idea           InputBehaviour       (artifact: app-idea)
[2] po-refinement-loop     Blueprint + AGENT_SIGNAL loop, max 8 iters → AWAIT_USER
        └─ po-refine       AgentBehaviour       (PO agent, CONTINUE/COMPLETE)
[3] po-finalise            AgentBehaviour       transition: HUMAN_APPROVAL
[4] ba-epics               AgentBehaviour       transition: HUMAN_APPROVAL
[5] architect-design       AgentBehaviour       transition: AUTO
[6] dev-architect-spar     Blueprint
        └─ dev-architect-review  SparBehaviour  challenger=Developer, defender=Architect, maxRounds=3
[7] architect-confirm      AgentBehaviour       transition: HUMAN_APPROVAL
[8] dev-plan               AgentBehaviour       (Developer; produces plan + skeleton files)
[9] tester-plan            AgentBehaviour       (Tester; produces testPlan + testCases)
[10] assemble-package      AgentBehaviour       (Assembler; CREATE_FILE deliverables)
```

## Framework Primitives Used

| Primitive | Where |
|---|---|
| `InputBehaviour` | step 1 — collects the seed idea |
| `AgentBehaviour` | steps 3, 4, 5, 7, 8, 9, 10 |
| `SparBehaviour` | step 6 — Dev challenges, Architect defends, max 3 rounds |
| `BlueprintRef` | po-refinement-loop, dev-architect-spar |
| `LoopConfig` (AGENT_SIGNAL) | po-refinement-loop — PO returns `COMPLETE` when ready |
| `MaxIterationsAction.AWAIT_USER` | po-refinement-loop — pauses for user if PO loops too long |
| `StepTransition.HUMAN_APPROVAL` | gates after po-finalise, ba-epics, architect-confirm |
| `ContextMapping` | every step declares input/output keys explicitly |

## Determinism Guarantees

- Only the **first step** has a hardcoded question (the seed idea).
- All subsequent user interaction comes from `USER_PROMPT` commands emitted by agents — never hardcoded.
- The **flow** is fixed: agents cannot skip steps, change the order, or invoke other agents directly. Each step has exactly one agent.
- The Dev↔Architect debate is **bounded** by `SparBehaviour.maxRounds=3`. No infinite loop possible.
- The PO refinement loop is bounded by `maxIterations=8` with `AWAIT_USER` fallback. No infinite loop possible.

## Context Key Discipline

| Key | Producer step | Consumer steps |
|---|---|---|
| `appIdea` | collect-idea | po-refine |
| `productVisionDraft` | po-refine (loop) | po-refine, po-finalise |
| `productVision` | po-finalise | ba-epics, architect-design, dev-architect-review, dev-plan, tester-plan, assemble-package |
| `epics` | ba-epics | architect-design, dev-architect-review, dev-plan, tester-plan, assemble-package |
| `architectureDesign` | architect-design, dev-architect-review, architect-confirm | dev-plan, assemble-package |
| `implementationPlan` | dev-plan | tester-plan, assemble-package |
| `testPlan`, `testCases` | tester-plan | assemble-package |

## Final Deliverables

The `assemble-package` step produces, via `CreateFileCommand` (downloadable via `GET /api/v1/runs/{id}/files/{fileId}`):

- `delivery/01-product-vision.json`
- `delivery/02-epics-and-stories.json`
- `delivery/03-architecture.json`
- `delivery/04-implementation-plan.json`
- `delivery/05-test-plan.json`
- `delivery/06-test-cases.json`
- `delivery/README.md`

The Developer step may additionally produce skeleton files under `dev/` (e.g. API contracts, top-level README).

## Installing as a Shipped Workflow

1. Copy `app-creator.workflow/` into `agentforge4j-workflows/src/main/resources/shipped-workflows/app-creator.workflow/`.
2. Append `app-creator` to `agentforge4j-workflows/src/main/resources/shipped-workflows/index`.
3. Copy each `*.agent/` directory into `agentforge4j-workflows/src/main/resources/shipped-agents/`.
4. Append the six agent ids to `agentforge4j-workflows/src/main/resources/shipped-agents/index`:
   ```
   app-creator-po-agent
   app-creator-ba-agent
   app-creator-architect-agent
   app-creator-developer-agent
   app-creator-tester-agent
   app-creator-assembler-agent
   ```
5. Extend `ClasspathWorkflowLoaderTest` to assert the `app-creator` workflow loads with the expected artifact (`app-idea`) and blueprints (`po-refinement-loop`, `dev-architect-spar`).

## Installing as a Filesystem Workflow

Drop `app-creator.workflow/` under your configured `agentforge4j.workflows-path` and each `*.agent/` directory under your `agentforge4j.agents-path`. No bundle index is required for filesystem loading.

## Notes for the Agent Prompts

- All agents specify `supportedCommands` in `agent.json`, which constrains the framework instruction layer in `AgentInvoker` to only the relevant command schema. This reduces token cost and reduces invalid-command rates.
- `SET_CONTEXT` values use the typed `ContextValue` shape — JSON-shaped values use `{"type": "JSON", "json": "..."}`. The PO, BA, Architect, Developer, and Tester prompts all show the correct shape.
- The Assembler uses `gpt-4o-mini` / `claude-3-5-haiku` — it is mechanical, no reasoning required.
- The PO, BA, Architect, Developer use `gpt-4o` / `claude-3-5-sonnet` first — these are reasoning-heavy.

## Things to Watch in Production

- `USER_PROMPT` with `responseRequired: false` is used as the human-readable summary at approval gates — make sure the UI renders these clearly.
- The Spar loop produces a conversation thread (Runner Epic 5 spec). Verify the UI shows challenger/defender turns clearly.
- The PO loop's `AWAIT_USER` fallback means if the PO over-asks, the run pauses for user direction. Confirm the UI surfaces this state usefully.
- Token usage in the Spar step scales linearly with `maxRounds` — the current setting (3) is balanced for cost vs convergence quality.

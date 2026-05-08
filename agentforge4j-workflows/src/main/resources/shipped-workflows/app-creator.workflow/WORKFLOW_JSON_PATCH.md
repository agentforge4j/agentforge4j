# `workflow.json` — Patch Instructions

Two categories of change. Apply both.

---

## Change 1 — Attach `stepPrompt` to four existing steps that didn't have one

These steps gain a `stepPrompt` field. **Nothing else on the step changes.** The new prompt files are listed in the Bundle Index Update section below.

### Step `ba-epics`

```json
{
  "kind": "STEP",
  "stepId": "ba-epics",
  "name": "BA Generates Epics and User Stories",
  "behaviour": {
    "type": "AGENT",
    "agentRef": "app-creator-ba-agent",
    "transition": "HUMAN_APPROVAL"
  },
  "stepPrompt": "ba-epics.step.prompt.md",
  "contextMapping": {
    "inputKeys": ["productVision"],
    "outputKeys": ["epics"]
  }
}
```

### Step `architect-design`

```json
{
  "kind": "STEP",
  "stepId": "architect-design",
  "name": "Architect Designs System",
  "behaviour": {
    "type": "AGENT",
    "agentRef": "app-creator-architect-agent",
    "transition": "AUTO"
  },
  "stepPrompt": "architect-design.step.prompt.md",
  "contextMapping": {
    "inputKeys": ["productVision", "epics"],
    "outputKeys": ["architectureDesign"]
  }
}
```

### Step `dev-plan`

```json
{
  "kind": "STEP",
  "stepId": "dev-plan",
  "name": "Developer Produces Implementation Plan",
  "behaviour": {
    "type": "AGENT",
    "agentRef": "app-creator-developer-agent",
    "transition": "AUTO"
  },
  "stepPrompt": "dev-plan.step.prompt.md",
  "contextMapping": {
    "inputKeys": ["productVision", "epics", "architectureDesign"],
    "outputKeys": ["implementationPlan"]
  }
}
```

### Step `tester-plan`

```json
{
  "kind": "STEP",
  "stepId": "tester-plan",
  "name": "Tester Produces Test Strategy and Cases",
  "behaviour": {
    "type": "AGENT",
    "agentRef": "app-creator-tester-agent",
    "transition": "AUTO"
  },
  "stepPrompt": "tester-plan.step.prompt.md",
  "contextMapping": {
    "inputKeys": ["productVision", "epics", "implementationPlan"],
    "outputKeys": ["testPlan", "testCases"]
  }
}
```

---

## Change 2 — Append the new `executive-summary` step at the end of `steps`

Insert this **after** `assemble-package`, as the final entry in the `steps` array:

```json
{
  "kind": "STEP",
  "stepId": "executive-summary",
  "name": "Produce Executive Summary",
  "behaviour": {
    "type": "AGENT",
    "agentRef": "app-creator-executive-agent",
    "transition": "AUTO"
  },
  "stepPrompt": "executive-summary.step.prompt.md",
  "contextMapping": {
    "inputKeys": ["productVision", "epics", "architectureDesign", "implementationPlan", "testPlan", "testCases"],
    "outputKeys": ["executiveSummary"]
  }
}
```

---

## Bundle Index Update

The bundle `index` file inside `app-creator.workflow/` must be updated to list the new files. Replace the existing `index` with:

```
app-idea.artifact.json
po-refinement-loop.blueprint.json
dev-architect-spar.blueprint.json
po-finalise.step.prompt.md
ba-epics.step.prompt.md
architect-design.step.prompt.md
architect-confirm.step.prompt.md
dev-plan.step.prompt.md
tester-plan.step.prompt.md
assemble-package.step.prompt.md
executive-summary.step.prompt.md
```

(Five new entries: `ba-epics`, `architect-design`, `dev-plan`, `tester-plan`, `executive-summary` step prompts.)

---

## Shipped Agents Index Update

If the bundle is installed as a shipped workflow, append the new agent id to `shipped-agents/index`:

```
app-creator-executive-agent
```

---

## What Did NOT Change

- Step ordering (other than the appended final step).
- Any `behaviour.type` value.
- Any `transition` value.
- Any blueprint definition (`po-refinement-loop`, `dev-architect-spar`).
- The artifact (`app-idea.artifact.json`).
- The Loop and Spar configurations.
- All `contextMapping` values on existing steps.
- The PO agent's `agent.json` and `boundaries.md`.
- The BA agent's `agent.json`.
- The Architect, Developer, Tester `agent.json` files.
- The Assembler agent's `agent.json` and `systemprompt.md`.

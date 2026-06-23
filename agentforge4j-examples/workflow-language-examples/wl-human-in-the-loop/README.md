# Human in the Loop

## What this teaches

How the workflow language expresses human-in-the-loop control: an `INPUT` step that suspends to
collect data from a person, and a `HUMAN_APPROVAL` gate that suspends for an approve/reject decision
— and the resume verbs that drive each forward. This is the workflow-language companion to
`framework-examples/human-approval`; that example covers the suspend/resume basics, this one focuses
on the language constructs and the resume-verb contract. No Spring, no real LLM keys, no network.

## AgentForge4j capability demonstrated

`InputBehaviour` and the `HUMAN_APPROVAL` step gate. The run suspends at `AWAITING_INPUT`;
`WorkflowRuntime.submitInput(runId, answers, actorId)` supplies the artifact's fields (stored in the
context under `<artifactId>.<itemId>`). The reviewer agent then finishes its step (a scripted
`COMPLETE` from `agentforge4j-llm-fake`) and the run suspends at `AWAITING_STEP_APPROVAL`;
`WorkflowRuntime.decideStepApproval(...)` decides: `Approve` → `COMPLETED`, `Reject` → `FAILED` (with
a `StepRejectionFailure` carrying the reason). These two gates use different resume verbs —
`submitInput` for input, `decideStepApproval` for the approval — which is the contract this example
makes concrete.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-human-in-the-loop -am verify
```

`verify` runs the deterministic tests, which assert both suspends and both terminal outcomes. To
watch both paths print, run `WlHumanInTheLoopExample.main` from your IDE.

## Expected behaviour / output

`main` runs the approve and reject paths and prints, for example:

```text
approve — after start: AWAITING_INPUT
approve — after input: AWAITING_STEP_APPROVAL
approve — after approve: COMPLETED
reject — after reject: FAILED
  failure reason: Needs rework
```

The bundled tests assert the `AWAITING_INPUT` → `AWAITING_STEP_APPROVAL` → terminal sequence, the
stored input value, and the `StepRejectionFailure` on reject — all deterministically.

## Files to read first

1. `src/main/resources/workflows/wl-human-in-the-loop.workflow/workflow.json` — the `INPUT` step and
   the agent step gated by `HUMAN_APPROVAL`.
2. `src/main/resources/workflows/wl-human-in-the-loop.workflow/request-form.artifact.json` — the
   input artifact whose fields `submitInput` supplies.
3. `src/main/java/.../WlHumanInTheLoopExample.java` — the start → submit input → decide approval flow
   for both outcomes.
4. `src/test/java/.../WlHumanInTheLoopExampleTest.java` — the deterministic assertions for both paths.

# Human Approval

## What this teaches

How a workflow suspends to wait for a human decision and then resumes — the core of any
human-in-the-loop process. The run pauses at an approval gate; approving it completes the run,
rejecting it fails the run. No Spring, no real LLM keys, no network.

## AgentForge4j capability demonstrated

Suspend/resume via a `HUMAN_APPROVAL` step gate. The agent finishes its step (a scripted `COMPLETE`
from the deterministic fake provider, `agentforge4j-llm-fake`), the run suspends at
`AWAITING_STEP_APPROVAL`, and `WorkflowRuntime.decideStepApproval(...)` drives it forward:
`Approve` → `COMPLETED`, `Reject` → `FAILED` (with a `StepRejectionFailure`). The deciding actor is
carried inside the decision.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl framework-examples/human-approval -am verify
```

`verify` runs the deterministic tests, which assert the suspend, the approved outcome, and the
rejected outcome. To watch both paths print, run `HumanApprovalExample.main` from your IDE.

## Expected behaviour / output

The run suspends at `AWAITING_STEP_APPROVAL`; approving reaches `COMPLETED`, rejecting reaches
`FAILED`. `main` runs both paths and prints, for example:

```text
approve path — after start: AWAITING_STEP_APPROVAL
approve path — after approve: COMPLETED
reject path — after start: AWAITING_STEP_APPROVAL
reject path — after reject: FAILED
  failure reason: Needs rework
```

The bundled test asserts the suspended and both terminal states deterministically.

## Files to read first

1. `src/main/java/.../HumanApprovalExample.java` — the start → suspend → decide flow for both the
   approve and reject paths.
2. `src/main/resources/workflows/human-approval.workflow/workflow.json` — the step gated with
   `HUMAN_APPROVAL`.
3. `src/main/resources/agents/human-approval-agent.agent/agent.json` — the agent that routes to the
   `fake` provider.
4. `src/test/java/.../HumanApprovalExampleTest.java` — the deterministic assertions for both
   outcomes.

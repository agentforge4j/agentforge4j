# Human in the Loop

## What this teaches

How the workflow language expresses human-in-the-loop control: an `INPUT` step that suspends to
collect data from a person, and a `HUMAN_APPROVAL` gate that suspends for an approve/reject decision
— and the resume verbs that drive each forward. This is the workflow-language companion to
`framework-examples/human-approval`; that example covers the suspend/resume basics, this one focuses
on the language constructs and the resume-verb contract. The example is LLM-agnostic: it runs offline
against a deterministic fake by default; pointing it at a real provider needs a provider module,
credentials, and the agent repointed away from the fake — no change to the workflow structure itself.
No Spring, no network on the default path.

## AgentForge4j capability demonstrated

`InputBehaviour` and the `HUMAN_APPROVAL` step gate. The run suspends at `AWAITING_INPUT`;
`WorkflowRuntime.submitInput(runId, answers, actorId)` supplies the artifact's fields (stored in the
context under `<artifactId>.<itemId>`). The reviewer agent then finishes its step (served by the LLM —
a scripted `COMPLETE` from `agentforge4j-llm-fake` offline, or a real model when configured) and the
run suspends at `AWAITING_STEP_APPROVAL`; `WorkflowRuntime.decideStepApproval(...)` decides: `Approve`
→ `COMPLETED`, `Reject` → `FAILED` (with a `StepRejectionFailure` carrying the reason). These two gates
use different resume verbs — `submitInput` for input, `decideStepApproval` for the approval — which is
the contract this example makes concrete.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-human-in-the-loop -am verify
```

`verify` runs the deterministic tests, which always use the bundled fake and assert both suspends and
both terminal outcomes.

**Offline (default).** `WlHumanInTheLoopApp.main` runs with no configuration: the `api-key` in
`src/main/resources/example.properties` is blank, so the deterministic `agentforge4j-llm-fake` provider
serves the reviewer agent's step — no key, no network, no extra dependency. Run it from your IDE to
watch both paths print.

**Against a real LLM.** Set a provider key — either `agentforge4j.example.llm.api-key` in
`example.properties`, or the `AGENTFORGE4J_EXAMPLE_LLM_API_KEY` environment variable (see `.env.example`)
— add a provider module dependency (for example `agentforge4j-llm-openai`) to this module's `pom.xml`,
and edit the `providerPreferences` in `src/main/resources/agents/reviewer-agent.agent/agent.json` to
name the chosen provider instead of `fake` (the agent ships pinned to the fake provider so the offline
default is deterministic). With those three changes made, the same workflow structure runs unchanged,
with the reviewer agent's step now served by the real model. The input and the approval decision are
still supplied in code — they are human gates, not model output. With a key set but no provider module
on the classpath, assembly fails fast with a clear "no provider factory" message; with the module
present but the agent still pinned to `fake`, the run fails at the first agent step with an
`LlmInvocationException` saying the agent has no available provider preferences. Precedence for every
value is system property, then environment variable, then `example.properties`.

## Expected behaviour / output

On the offline fake path, `main` runs the approve and reject paths and prints, for example:

```text
approve — after start: AWAITING_INPUT
approve — after input: AWAITING_STEP_APPROVAL
approve — after approve: COMPLETED
reject — after reject: FAILED
  failure reason: Needs rework
```

The bundled tests assert the `AWAITING_INPUT` → `AWAITING_STEP_APPROVAL` → terminal sequence, the
stored input value, and the `StepRejectionFailure` on reject — all deterministically against the fake.

## Files to read first

1. `src/main/resources/workflows/wl-human-in-the-loop.workflow/workflow.json` — the `INPUT` step and
   the agent step gated by `HUMAN_APPROVAL`.
2. `src/main/resources/workflows/wl-human-in-the-loop.workflow/request-form.artifact.json` — the
   input artifact whose fields `submitInput` supplies.
3. `src/main/java/.../WlHumanInTheLoopApp.java` — the start → submit input → decide approval flow
   for both outcomes, resolving fake vs. real.
4. `src/main/java/.../WlHumanInTheLoopFakeLlm.java` — the single source of truth for the offline
   scripted reviewer response.
5. `src/main/java/.../ExampleLlmConfig.java` — how the fake/real toggle, provider, and key are resolved.
6. `src/test/java/.../WlHumanInTheLoopAppTest.java` — the deterministic assertions for both paths.

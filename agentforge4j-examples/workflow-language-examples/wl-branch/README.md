# Branch Routing

## What this teaches

How a workflow makes a deterministic, author-controlled routing decision in the workflow language —
not an LLM picking the next step at runtime, but a `BRANCH` step that reads a value from the context
and dispatches to one of several targets. It also shows `FAIL` as an explicit terminal. The example is
LLM-agnostic: it runs offline against a deterministic fake by default; pointing it at a real provider
needs a provider module, credentials, and the agents repointed away from the fake — no change to the
workflow structure itself. No Spring, no network on the default path.

## AgentForge4j capability demonstrated

`BranchBehaviour` and `FailBehaviour`. An agent step (`decide`) writes the `decision` context key
with a `SET_CONTEXT` command; the `BRANCH` step's `contextKey` reads that value and matches
it against its `branches` map. The `approve` branch is an inline agent step that finishes the run
(`COMPLETED`); the `reject` branch is an inline `FAIL` step that ends the run (`FAILED`). The branch
step itself calls no model — routing is pure, deterministic dispatch.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-branch -am verify
```

`verify` runs the deterministic tests, which always use the bundled fake and assert both routes.

**Offline (default).** `WlBranchApp.main` runs with no configuration: the `api-key` in
`src/main/resources/example.properties` is blank, so the deterministic `agentforge4j-llm-fake` provider
is used — no key, no network, no extra dependency. Run it from your IDE to watch both paths print.

**Against a real LLM.** Set a provider key — either `agentforge4j.example.llm.api-key` in
`example.properties`, or the `AGENTFORGE4J_EXAMPLE_LLM_API_KEY` environment variable (see `.env.example`)
— add a provider module dependency (for example `agentforge4j-llm-openai`) to this module's `pom.xml`,
and edit the `providerPreferences` in `src/main/resources/agents/branch-agent.agent/agent.json` and
`src/main/resources/agents/approve-agent.agent/agent.json` to name the chosen provider instead of `fake`
(both agents ship pinned to the fake provider so the offline default is deterministic). With those three
changes made, the same workflow structure runs unchanged, and the real model now makes the `decide`
decision itself. With a key set but no provider module on the classpath, assembly fails fast with a
clear "no provider factory" message; with the module present but the agents still pinned to `fake`,
the run fails at the first agent step with an `LlmInvocationException` saying the agent has no
available provider preferences. Precedence for every value is system property, then environment
variable, then `example.properties`.

## Expected behaviour / output

On the offline fake path, `main` runs the workflow once per decision and prints:

```text
decision=approve -> status=COMPLETED
decision=reject -> status=FAILED
```

On the real-provider path the model decides, so the workflow runs once and prints the resulting status.
The bundled test asserts both terminal states and the routing value deterministically against the fake.

## Files to read first

1. `src/main/java/.../WlBranchApp.java` — the entry point: resolves fake vs. real, then assembles the
   runtime and runs the workflow.
2. `src/main/java/.../WlBranchFakeLlm.java` — the single source of truth for the offline scripted
   responses (the `decide` agent's decision and the approved branch's `COMPLETE`).
3. `src/main/java/.../ExampleLlmConfig.java` — how the fake/real toggle, provider, and key are resolved.
4. `src/main/resources/workflows/wl-branch.workflow/workflow.json` — the `BRANCH` step, its
   `contextKey`, and the two inline branch targets (an agent step and a `FAIL` step).
5. `src/test/java/.../WlBranchAppTest.java` — the deterministic assertions for both routes.

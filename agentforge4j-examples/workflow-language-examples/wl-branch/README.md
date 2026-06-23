# Branch Routing

## What this teaches

How a workflow makes a deterministic, author-controlled routing decision in the workflow language —
not an LLM picking the next step at runtime, but a `BRANCH` step that reads a value from the context
and dispatches to one of several targets. It also shows `FAIL` as an explicit terminal. No Spring,
no real LLM keys, no network.

## AgentForge4j capability demonstrated

`BranchBehaviour` and `FailBehaviour`. An agent step (`decide`) writes the `decision` context key
with a scripted `SET_CONTEXT` command; the `BRANCH` step's `contextKey` reads that value and matches
it against its `branches` map. The `approve` branch is an inline agent step that finishes the run
(`COMPLETED`); the `reject` branch is an inline `FAIL` step that ends the run (`FAILED`). The branch
step itself calls no model — routing is pure, deterministic dispatch.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-branch -am verify
```

`verify` runs the deterministic tests, which assert both routes. To watch both paths print, run
`WlBranchExample.main` from your IDE.

## Expected behaviour / output

`main` runs the workflow once per decision and prints, for example:

```text
decision=approve -> status=COMPLETED
decision=reject -> status=FAILED
```

The bundled test asserts both terminal states and the routing value deterministically.

## Files to read first

1. `src/main/java/.../WlBranchExample.java` — assembles the runtime and scripts the `decide` agent's
   decision so each branch can be driven from one workflow.
2. `src/main/resources/workflows/wl-branch.workflow/workflow.json` — the `BRANCH` step, its
   `contextKey`, and the two inline branch targets (an agent step and a `FAIL` step).
3. `src/main/resources/agents/branch-agent.agent/agent.json` — the routing agent that writes the
   decision via `SET_CONTEXT`.
4. `src/test/java/.../WlBranchExampleTest.java` — the deterministic assertions for both routes.

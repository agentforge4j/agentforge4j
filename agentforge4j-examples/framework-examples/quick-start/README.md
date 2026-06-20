# Quick Start

## What this teaches

The shortest path from nothing to a running workflow: assemble an AgentForge4j runtime in plain
Java, start a workflow, and observe it reach its terminal state — no Spring, no real LLM keys, no
network.

## AgentForge4j capability demonstrated

Framework-agnostic bootstrap (`AgentForge4jBootstrap.defaults()…build()`) running a workflow to
completion, driven by the deterministic fake LLM provider (`agentforge4j-llm-fake`) so the run is
fully reproducible. The fake is wired through the public `withLlmClientResolver(...)` seam; the
agent declares the `fake` provider, and a scripted `COMPLETE` command finishes the single step.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl framework-examples/quick-start -am verify
```

`verify` runs the deterministic test, which asserts the workflow reaches `COMPLETED`. To watch it
print its result, run `QuickStartExample.main` from your IDE.

## Expected behaviour / output

The workflow runs end to end and reaches `WorkflowStatus.COMPLETED`. `main` prints, for example:

```text
Workflow 'quick-start' (run <id>) finished with status: COMPLETED
Step outputs: {}
```

The step output map is empty by design: the agent emits only a `COMPLETE` command, which finishes
the step without writing a step output, so `getStepOutputs()` returns an empty map.

The bundled test asserts the terminal status deterministically.

## Design notes

- **Run from source.** This example is meant to be run from source — your IDE or Maven
  (`./mvnw … verify`, or `QuickStartExample.main` from the IDE). Running it from a packaged fat jar
  is intentionally not a design goal: the workflow and agent resources are resolved as filesystem
  paths, which only holds when running from exploded `target/classes`.
- **Minimal startup error handling.** `main` lets setup exceptions propagate rather than wrapping
  them in try/catch or logging. The example deliberately favours the shortest readable code over
  production-grade startup error handling.

## Files to read first

1. `src/main/java/.../QuickStartExample.java` — the assemble → start → read-state flow, and how the
   fake provider is scripted.
2. `src/main/resources/workflows/quick-start.workflow/workflow.json` — the one-step workflow.
3. `src/main/resources/agents/quick-start-agent.agent/agent.json` — the agent that routes to the
   `fake` provider.
4. `src/test/java/.../QuickStartExampleTest.java` — the deterministic assertion.

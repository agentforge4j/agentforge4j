# Loop

## What this teaches

How the workflow language expresses bounded iteration through a looped blueprint, and how the
termination strategy decides when the loop stops. Two variants are shown side by side: a fixed-count
loop and an agent-signalled loop. The example is LLM-agnostic: it runs offline against a deterministic
fake by default, or against a real provider with no code change. No Spring, no network on the default
path.

## AgentForge4j capability demonstrated

A looped blueprint: a workflow includes a `BLUEPRINT_REF`, and the referenced blueprint's
`behaviour.loopConfig` makes its body repeat. Two `terminationStrategy` values:

- **`FIXED_COUNT`** (`wl-loop-fixed`) — the body runs exactly `maxIterations` times; any agent
  completion signal is ignored.
- **`AGENT_SIGNAL`** (`wl-loop-signal`) — the body runs until the agent emits a `COMPLETE` signal,
  bounded by `maxIterations`. Here the agent signals on its first iteration, so the loop stops at one
  iteration, well before the ceiling — the agent, not a fixed count, decides when to stop. The
  `maxIterations: 5` on this loop is a safety bound that is never reached here; had the agent never
  signalled, the loop would stop at that ceiling and `FAIL` (its `maxIterationsAction`).

Iteration counts are read by tallying `LOOP_ITERATION_STARTED` events rather than inferred, so the
contrast between the two strategies is asserted directly. The event log is reached through
`components().workflowEventLog()` — the internal integrator accessor, not part of the public API; an
embedding application would normally surface run progress through its own projection.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-loop -am verify
```

`verify` runs the deterministic tests, which always use the bundled fake and assert each loop's
iteration count and completion.

**Offline (default).** `WlLoopApp.main` runs with no configuration: the `api-key` in
`src/main/resources/example.properties` is blank, so the deterministic `agentforge4j-llm-fake` provider
serves the loop body's agent calls — no key, no network, no extra dependency. Run it from your IDE to
watch both loops print.

**Against a real LLM.** Set a provider key — either `agentforge4j.example.llm.api-key` in
`example.properties`, or the `AGENTFORGE4J_EXAMPLE_LLM_API_KEY` environment variable (see `.env.example`)
— **and** add a provider module dependency (for example `agentforge4j-llm-openai`) to this module's
`pom.xml`. No code changes: the same workflows run, with the loop body served by the real model (so the
agent-signal loop now stops when the real model emits `COMPLETE`). With a key set but no provider module
on the classpath, the run fails fast with a clear "no provider factory" message. Precedence for every
value is system property, then environment variable, then `example.properties`.

## Expected behaviour / output

On the offline fake path, `main` runs both loops and prints, for example:

```text
wl-loop-fixed -> status=COMPLETED, iterations=3
wl-loop-signal -> status=COMPLETED, iterations=1
```

The bundled tests assert exactly 3 iterations for the fixed-count loop and exactly 1 for the
agent-signal loop, both reaching `COMPLETED`, deterministically against the fake.

## Files to read first

1. `src/main/resources/workflows/wl-loop-fixed.workflow/fixed-body.blueprint.json` — the
   `FIXED_COUNT` `loopConfig` on the blueprint behaviour.
2. `src/main/resources/workflows/wl-loop-signal.workflow/signal-body.blueprint.json` — the
   `AGENT_SIGNAL` `loopConfig`.
3. `src/main/java/.../WlLoopApp.java` — runs both loops and reads iteration counts from the event log,
   resolving fake vs. real.
4. `src/main/java/.../WlLoopFakeLlm.java` — the single source of truth for the offline scripted
   per-iteration responses.
5. `src/main/java/.../ExampleLlmConfig.java` — how the fake/real toggle, provider, and key are resolved.
6. `src/test/java/.../WlLoopAppTest.java` — the deterministic iteration-count assertions.

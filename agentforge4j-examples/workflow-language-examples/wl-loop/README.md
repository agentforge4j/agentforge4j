# Loop

## What this teaches

How the workflow language expresses bounded iteration through a looped blueprint, and how the
termination strategy decides when the loop stops. Two variants are shown side by side: a fixed-count
loop and an agent-signalled loop. No Spring, no real LLM keys, no network.

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

`verify` runs the deterministic tests, which assert each loop's iteration count and completion. To
watch both print, run `WlLoopExample.main` from your IDE.

## Expected behaviour / output

`main` runs both loops and prints, for example:

```text
wl-loop-fixed -> status=COMPLETED, iterations=3
wl-loop-signal -> status=COMPLETED, iterations=1
```

The bundled tests assert exactly 3 iterations for the fixed-count loop and exactly 1 for the
agent-signal loop, both reaching `COMPLETED`, deterministically.

## Files to read first

1. `src/main/resources/workflows/wl-loop-fixed.workflow/fixed-body.blueprint.json` — the
   `FIXED_COUNT` `loopConfig` on the blueprint behaviour.
2. `src/main/resources/workflows/wl-loop-signal.workflow/signal-body.blueprint.json` — the
   `AGENT_SIGNAL` `loopConfig`.
3. `src/main/java/.../WlLoopExample.java` — scripts one response per iteration and reads iteration
   counts from the event log.
4. `src/test/java/.../WlLoopExampleTest.java` — the deterministic iteration-count assertions.

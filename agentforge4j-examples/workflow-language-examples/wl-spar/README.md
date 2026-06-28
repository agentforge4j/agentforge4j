# SPAR

## What this teaches

How a `SPAR` step orchestrates two agents reasoning against each other under workflow control — a
primary and a challenger exchange views over bounded rounds, and the primary then resolves. It also
shows how the exchange decides to run another round. No Spring, no real LLM keys, no network.

## AgentForge4j capability demonstrated

`SparBehaviour`. The step names a primary (`agentRef`) and, in `sparConfig`, a `challengerAgentId`,
`maxRounds`, and a `resolutionPrompt`. Each round the primary speaks, then the challenger; the
exchange continues to another round while a side emits a `CONTINUE` command with `wantsAnotherRound`
true and a concrete, non-trivial `reason` (vague or too-short reasons do not justify another round).
Once the rounds finish, the primary performs the resolution turn and completes the step. Each round's
contributions are recorded in the context under `spar.primary.round.N` and `spar.challenger.round.N`.

In this example `maxRounds` is 2 and both sides ask for a second round in round one, so the exchange
runs both rounds and then resolves — a genuine multi-round SPAR, made deterministic by scripting each
agent's per-round turn (ordinals advance independently per agent). Because `maxRounds` is 2, round two
is the last round regardless of its responses — the runtime only checks for another round *before* the
final round — so the round-two decline reads as the natural close rather than the binding stop: the cap
binds at the ceiling.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-spar -am verify
```

`verify` runs the deterministic test, which asserts both rounds ran and the run resolved. To watch it
print, run `WlSparExample.main` from your IDE.

## Expected behaviour / output

`main` runs the SPAR workflow and prints:

```text
status=COMPLETED
```

The bundled test asserts the run reaches `COMPLETED` and that both the primary and the challenger
contributed in each of the two rounds (`spar.primary.round.1/2`, `spar.challenger.round.1/2`),
deterministically.

## Files to read first

1. `src/main/resources/workflows/wl-spar.workflow/workflow.json` — the `SPAR` step and its
   `sparConfig` (`challengerAgentId`, `maxRounds`, `resolutionPrompt`).
2. `src/main/java/.../WlSparExample.java` — the per-agent, per-round scripted turns and the resolution.
3. `src/test/java/.../WlSparExampleTest.java` — the deterministic round-by-round assertions.

# SPAR

## What this teaches

How a `SPAR` step orchestrates two agents reasoning against each other under workflow control — a
primary and a challenger exchange views over bounded rounds, and the primary then resolves. It also
shows how the exchange decides to run another round. The example is LLM-agnostic: it runs offline
against a deterministic fake by default, or against a real provider with no code change. No Spring, no
network on the default path.

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

`verify` runs the deterministic test, which always uses the bundled fake and asserts both rounds ran
and the run resolved.

**Offline (default).** `WlSparApp.main` runs with no configuration: the `api-key` in
`src/main/resources/example.properties` is blank, so the deterministic `agentforge4j-llm-fake` provider
serves every agent turn — no key, no network, no extra dependency. Run it from your IDE to watch the
exchange resolve and print.

**Against a real LLM.** Set a provider key — either `agentforge4j.example.llm.api-key` in
`example.properties`, or the `AGENTFORGE4J_EXAMPLE_LLM_API_KEY` environment variable (see `.env.example`)
— **and** add a provider module dependency (for example `agentforge4j-llm-openai`) to this module's
`pom.xml`. No code changes: the same workflow runs, with both agents' turns now served by the real
model. With a key set but no provider module on the classpath, the run fails fast with a clear "no
provider factory" message. Precedence for every value is system property, then environment variable,
then `example.properties`.

## Expected behaviour / output

On the offline fake path, `main` runs the SPAR workflow and prints:

```text
status=COMPLETED
```

The bundled test asserts the run reaches `COMPLETED` and that both the primary and the challenger
contributed in each of the two rounds (`spar.primary.round.1/2`, `spar.challenger.round.1/2`),
deterministically against the fake.

## Files to read first

1. `src/main/resources/workflows/wl-spar.workflow/workflow.json` — the `SPAR` step and its
   `sparConfig` (`challengerAgentId`, `maxRounds`, `resolutionPrompt`).
2. `src/main/java/.../WlSparApp.java` — the run that resolves fake vs. real and prints the status.
3. `src/main/java/.../WlSparFakeLlm.java` — the single source of truth for the per-agent, per-round
   scripted turns and the resolution.
4. `src/main/java/.../ExampleLlmConfig.java` — how the fake/real toggle, provider, and key are resolved.
5. `src/test/java/.../WlSparAppTest.java` — the deterministic round-by-round assertions.

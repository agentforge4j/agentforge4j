# Retry Previous Step

## What this teaches

How `RETRY_PREVIOUS` rewinds a workflow and re-executes an earlier step, bounded by a maximum number
of attempts, with a fallback once those attempts are exhausted. The example is LLM-agnostic: it runs
offline against a deterministic fake by default, or against a real provider with no code change. No
Spring, no network on the default path.

## AgentForge4j capability demonstrated

`RetryPreviousBehaviour`. The `request` step collects a note via `INPUT`; the `retry` step
(`retryStepId: request`, `retryMode: FROM_STEP`, `maxAttempts: 1`) rewinds the run to the request and
re-executes it — so the run re-suspends at `AWAITING_INPUT` and the input is requested a second time.
On the next pass the single attempt is exhausted, so the run falls through to the `fallback` agent
step and reaches `COMPLETED`.

The re-executed step is an `INPUT` step on purpose: `RETRY_PREVIOUS` rewinds and re-runs the previous
step, and a re-run `INPUT` step re-suspends — which is the visible proof that the rewind happened.
This mirrors the framework's own retry verification fixture, where the rewind target is likewise a
pausing step.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-language-examples/wl-retry -am verify
```

`verify` runs the deterministic test, which always uses the bundled fake and asserts the re-suspend
and the eventual completion.

**Offline (default).** `WlRetryApp.main` runs with no configuration: the `api-key` in
`src/main/resources/example.properties` is blank, so the deterministic `agentforge4j-llm-fake` provider
serves the fallback agent's step — no key, no network, no extra dependency. Run it from your IDE to
watch the rewind and completion print.

**Against a real LLM.** Set a provider key — either `agentforge4j.example.llm.api-key` in
`example.properties`, or the `AGENTFORGE4J_EXAMPLE_LLM_API_KEY` environment variable (see `.env.example`)
— **and** add a provider module dependency (for example `agentforge4j-llm-openai`) to this module's
`pom.xml`. No code changes: the same workflow runs, with the fallback agent's step now served by the
real model; the inputs are still supplied in code. With a key set but no provider module on the
classpath, the run fails fast with a clear "no provider factory" message. Precedence for every value is
system property, then environment variable, then `example.properties`.

## Expected behaviour / output

On the offline fake path, `main` supplies the note twice (the original request and the retry's
re-request) and prints, for example:

```text
after start: AWAITING_INPUT
after first input (retry rewinds, re-requests): AWAITING_INPUT
after second input (attempts exhausted, fallback): COMPLETED
```

The bundled test asserts the `AWAITING_INPUT` → `AWAITING_INPUT` → `COMPLETED` sequence
deterministically against the fake.

## Files to read first

1. `src/main/resources/workflows/wl-retry.workflow/workflow.json` — the `INPUT` step and the
   `RETRY_PREVIOUS` step with its `retryStepId`, `retryMode`, `maxAttempts`, and `fallback`.
2. `src/main/java/.../WlRetryApp.java` — the start → input → re-suspend → input → complete flow,
   resolving fake vs. real.
3. `src/main/java/.../WlRetryFakeLlm.java` — the single source of truth for the offline scripted
   fallback response.
4. `src/main/java/.../ExampleLlmConfig.java` — how the fake/real toggle, provider, and key are resolved.
5. `src/test/java/.../WlRetryAppTest.java` — the deterministic assertions.

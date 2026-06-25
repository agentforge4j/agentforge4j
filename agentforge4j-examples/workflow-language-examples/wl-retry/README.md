# Retry Previous Step

## What this teaches

How `RETRY_PREVIOUS` rewinds a workflow and re-executes an earlier step, bounded by a maximum number
of attempts, with a fallback once those attempts are exhausted. No Spring, no real LLM keys, no
network.

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

`verify` runs the deterministic test, which asserts the re-suspend and the eventual completion. To
watch it print, run `WlRetryExample.main` from your IDE.

## Expected behaviour / output

`main` supplies the note twice (the original request and the retry's re-request) and prints, for
example:

```text
after start: AWAITING_INPUT
after first input (retry rewinds, re-requests): AWAITING_INPUT
after second input (attempts exhausted, fallback): COMPLETED
```

The bundled test asserts the `AWAITING_INPUT` → `AWAITING_INPUT` → `COMPLETED` sequence
deterministically.

## Files to read first

1. `src/main/resources/workflows/wl-retry.workflow/workflow.json` — the `INPUT` step and the
   `RETRY_PREVIOUS` step with its `retryStepId`, `retryMode`, `maxAttempts`, and `fallback`.
2. `src/main/java/.../WlRetryExample.java` — the start → input → re-suspend → input → complete flow.
3. `src/test/java/.../WlRetryExampleTest.java` — the deterministic assertions.

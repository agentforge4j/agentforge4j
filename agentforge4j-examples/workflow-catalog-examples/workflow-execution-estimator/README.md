# Execution Estimator

## What this teaches

How to use a **shipped catalog workflow** rather than authoring your own, and how a caller is
expected to behave around it: analyse a target workflow deterministically, run the shipped
estimator bundle to its approval pause, aggregate the sized figures it produced, and show the full
disclosure — token range, minimum required tokens, confidence, complexity, risk flags, and a
continue/narrow/stop recommendation — before deciding whether to approve. No network calls, no
real LLM keys, and no external service dependency.

## AgentForge4j capability demonstrated

The shipped `workflow-execution-estimator` bundle and its `execution-estimator` agent, loaded from
the catalog (`agentforge4j-workflows-catalog`) rather than from local resources. The example plays
the "AI Agent Adoption Center" — an application that gives new baby AI Agents new homes — and asks
the estimator:

> "Estimate the execution size of a workflow that gives birth to a new baby AI Agent for the AI
> Agent Adoption Center. The baby agent needs a cute name, personality, quirks, care instructions,
> training needs, and an adoption profile. The workflow should include a human review before the
> baby agent becomes available for adoption."

The target workflow this describes (`baby-agent-birth`) never runs — it exists only as data for the
deterministic structural analyser to examine. The estimator workflow is the only thing that
executes, and this example is the **compliant caller** its own contract requires: it reads the
estimator's sized figures at the approval pause, combines them with the target's structural
analysis, and discloses the result before ever deciding to approve.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework (and the shipped
catalog) into your local `.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-catalog-examples/workflow-execution-estimator -am verify
```

`verify` runs the deterministic test, which asserts the disclosed estimate is well-formed. To see the
disclosure printed, run `WorkflowExecutionEstimatorExample.main` from your IDE.

## Expected behaviour / output

`main` prints the full disclosure before the approval decision, for example:

```text
--- Execution estimate (disclosed before approval) ---
complexity: MODERATE
confidence: HIGH
estimatedMinTokens: 7600
estimatedExpectedTokens: 7600
estimatedMaxTokens: 7600
minimumRequiredTokens: 1300
estimatedAgentTurns: 6
estimatedToolInvocations: 0
estimatedSteps: 7
riskFlags: []
recommendation: CONTINUE
```

The bundled test asserts the estimate's shape (ordered token envelope, positive minimum floor,
`CONTINUE` recommendation for this bounded target) deterministically.

## Files to read first

1. `src/main/java/.../WorkflowExecutionEstimatorExample.java` — loads the target workflow as data,
   assembles the runtime with the shipped catalog enabled and the estimator agent's response
   scripted, and shows the compliant-caller sequence: analyse → run to the pause → read the sized
   figures → aggregate → disclose → decide.
2. `src/main/resources/target-workflow/baby-agent-birth.workflow.json` — the target workflow being
   estimated (never executed).
3. `src/test/java/.../WorkflowExecutionEstimatorExampleTest.java` — the deterministic assertions on
   the disclosed estimate.

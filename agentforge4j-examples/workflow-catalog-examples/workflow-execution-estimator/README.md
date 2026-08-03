# Execution Estimator

## What this teaches

How to use a **shipped catalog workflow** rather than authoring your own, and how a caller is
expected to behave around it: analyse a target workflow deterministically, supply that analysis as
input to the shipped estimator bundle, and let its `aggregate-estimate` step combine the analysis
with the agent's sizing into the full disclosure — token range, minimum required tokens,
confidence, complexity, risk flags, and a continue/narrow/stop recommendation — before the run
pauses; the caller then reads and shows that disclosure before deciding whether to approve. No
network calls, no real LLM keys, and no external service dependency.

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
executes, and this example is the **compliant caller** its own contract requires: it supplies the
target's structural analysis as input to the run, the bundle's `aggregate-estimate` step combines
that analysis with the agent's sizing into the full disclosure before the run pauses for approval,
and the example reads and shows that disclosure before ever deciding to approve.

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
riskFlags: 
iterationCeiling: 1
recommendation: CONTINUE
```

The bundled test asserts the estimate's shape (ordered token envelope, positive minimum floor,
`CONTINUE` recommendation for this bounded target) deterministically.

## Files to read first

1. `src/main/java/.../WorkflowExecutionEstimatorExample.java` — loads the target workflow as data,
   assembles the runtime with the shipped catalog enabled and the estimator agent's response
   scripted, and shows the compliant-caller sequence: analyse → submit the analysis as input → run
   to the pause (the workflow's own `aggregate-estimate` step aggregates) → read the disclosed
   estimate → disclose → decide.
2. `src/main/resources/target-workflow/baby-agent-birth.workflow.json` — the target workflow being
   estimated (never executed).
3. `src/test/java/.../WorkflowExecutionEstimatorExampleTest.java` — the deterministic assertions on
   the disclosed estimate.

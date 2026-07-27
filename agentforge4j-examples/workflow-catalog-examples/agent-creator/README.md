# Agent Creator

## What this teaches

How to use a **shipped catalog workflow** rather than authoring your own, and how a caller is
expected to behave around its approval gate: submit a freeform agent idea, run the shipped
`agent-creator` bundle to its approval pause, disclose the design preview, recommended tier, and
token estimate it produced, and only then decide whether to approve. No network calls, no real LLM
keys, and no external service dependency.

## AgentForge4j capability demonstrated

The shipped `agent-creator` bundle, loaded from the catalog (`agentforge4j-workflows-catalog`)
rather than from local resources — including its five agents shared from the top-level
`shipped-agents/` catalog (`requirement-structurer`, `complexity-risk-assessor`,
`design-previewer`, `bundle-reviewer`, `verification-author`), alongside its two workflow-local
agents (`agent-author`, `token-estimator`). The example plays the "AI Agent Adoption Center" and
asks `agent-creator`:

> "Create an agent that writes a short, warm welcome message for a family that has just adopted a
> baby AI Agent from the AI Agent Adoption Center, personalized with the agent's name and its
> quirks."

`agent-creator` deterministically resolves this to a LITE-tier bundle (simple/low risk, rule
`COMPLEXITY_RISK_MATRIX`). The example is the **compliant caller** the workflow's approval gate is
designed around: it reads the disclosed design preview, recommended tier, and token estimate at the
pause, shows that disclosure, and only then approves — never approving first and disclosing after.
Approval lets the workflow generate and deterministically validate the created-agent bundle
(`agent.json`, `systemprompt.md`, `README.md`, and a verification starter) as an export package
under `shipped-agents/generated.agent/`, not a live catalog write — see the generated `README.md`'s
own install instructions for how a human would place it into the real catalog.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework (and the shipped
catalog) into your local `.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl workflow-catalog-examples/agent-creator -am verify
```

`verify` runs the deterministic test, which asserts the run completes and the bundle is approved. To
see the disclosure and outcome printed, run `AgentCreatorExample.main` from your IDE.

## Expected behaviour / output

`main` prints the full disclosure before the approval decision, then the outcome, for example:

```text
--- Design preview and estimate (disclosed before approval) ---
agentId: adoption-welcome-writer
recommendedTier: LITE
ruleFired: COMPLEXITY_RISK_MATRIX
designSummary: A LITE-tier welcome-message writer for the AI Agent Adoption Center: given a newly adopted baby AI Agent's name and quirks, it returns a short, warm, personalized welcome message. No special boundaries beyond staying warm and factual.
tokenEstimate: {"estimatedInputTokens": 400, "estimatedOutputTokens": 150, "estimatedTotalTokens": 550, "estimatedTokenRange": {"low": 400, "high": 800}, "estimationConfidence": "high", "modelTier": "LITE", "assumptions": ["a short adoption-day welcome message", "single-pass generation"]}
--- Outcome ---
status: COMPLETED
verdict: PASS
```

The bundled test asserts the run reaches `COMPLETED` deterministically.

## Files to read first

1. `src/main/java/.../AgentCreatorExample.java` — assembles the runtime with the shipped catalog
   (workflows and agents) enabled and every agent-creator step's response scripted, and shows the
   compliant-caller sequence: submit the idea → run to the pause → disclose → decide → outcome.
2. `src/main/resources/agent-creator-script.json` — the scripted fake-LLM responses for every
   `agent-creator` step, themed on the AI Agent Adoption Center.
3. `src/test/java/.../AgentCreatorExampleTest.java` — the deterministic assertion that the run
   completes.

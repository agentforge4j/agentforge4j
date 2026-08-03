# Workflow Catalog Examples

Demonstrations of the **shipped catalog** of ready-made AgentForge4j workflows — how to discover,
configure, and run the workflows the framework provides out of the box, rather than authoring your
own from scratch. Each example is a standalone Maven module that runs deterministically and offline
against the shipped fake LLM provider — no network, no API keys — following the same five-section
README format as the framework examples.

## Examples

| Module | Shipped workflow | What it teaches |
|---|---|---|
| [`agent-creator`](agent-creator) | `agent-creator` | Submitting a freeform agent idea to the shipped agent-creator workflow, and how a caller discloses the design preview, recommended tier, and token estimate before deciding whether to approve. |
| [`workflow-execution-estimator`](workflow-execution-estimator) | `workflow-execution-estimator` | Estimating the execution shape of a target workflow before running it, and how a caller reads the workflow's own in-workflow aggregated disclosure before deciding whether to approve. |

## Running

These modules consume the published AgentForge4j artifacts (including the independently-versioned
shipped-workflow catalog) the way a third-party application would, so install the OSS reactor to your
local `.m2` first, then build the examples tree:

```bash
# from the OSS repository root
./mvnw install -DskipTests

# then, from agentforge4j-examples/
./mvnw verify
```

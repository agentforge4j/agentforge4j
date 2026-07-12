# Workflow Language Examples

Demonstrations of the declarative AgentForge4j workflow language: how to express control flow and
structure in workflow definitions (and blueprints) rather than in Java. Each example is a standalone
Maven module that runs deterministically and offline against the shipped fake LLM provider by
default — no network, no API keys — following the same five-section README format as the framework
examples. Five of the six (all but `wl-resource`, which never invokes an agent) can also run against a
real LLM provider with no code change, by configuring a provider and API key; see any of those
modules' READMEs for the pattern.

## Examples

| Module | Language feature(s) | What it teaches |
|---|---|---|
| [`wl-branch`](wl-branch) | `BRANCH`, `FAIL` | Author-controlled deterministic routing on a context value, with `FAIL` as an explicit terminal. |
| [`wl-resource`](wl-resource) | `RESOURCE` | A deterministic non-AI step that loads a bundled resource into the workflow context — not every step calls the LLM. |
| [`wl-human-in-the-loop`](wl-human-in-the-loop) | `INPUT`, `HUMAN_APPROVAL` | Suspend/resume human-in-the-loop: an `INPUT` gate then a `HUMAN_APPROVAL` gate, each with its own resume verb (`submitInput`, `decideStepApproval`). |
| [`wl-retry`](wl-retry) | `RETRY_PREVIOUS` | Rewind and re-execute a prior step, bounded by `maxAttempts`, with a fallback once attempts are exhausted. |
| [`wl-loop`](wl-loop) | blueprint loop (`FIXED_COUNT`, `AGENT_SIGNAL`) | Bounded iteration via a looped blueprint, with two termination strategies: a fixed count, and stopping on the agent's signal. |
| [`wl-spar`](wl-spar) | `SPAR` | Two agents (primary and challenger) in a bounded multi-round exchange under workflow control, then a resolution. |

`agent-signal` is not a standalone step behaviour — it is the `AGENT_SIGNAL` loop-termination
strategy, demonstrated as a variant within `wl-loop`.

## Running

These modules consume the published AgentForge4j artifacts the way a third-party application would,
so install the OSS reactor to your local `.m2` first, then build the examples tree:

```bash
# from the OSS repository root
./mvnw install -DskipTests

# then, from agentforge4j-examples/
./mvnw verify
```

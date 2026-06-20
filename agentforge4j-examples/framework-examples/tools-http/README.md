# HTTP Tool

## What this teaches

How to give an agent an HTTP-backed tool: the agent emits a tool call, the framework performs the
HTTP request, and the response is recorded in the run context. The example talks to a loopback HTTP
server it starts itself, so it stays deterministic and needs no external service, no real network,
and no keys.

## AgentForge4j capability demonstrated

The `agentforge4j-tools-http` provider, wired via `AgentForge4jBootstrap.withToolProviders(...)`. The
agent's single scripted response is a two-command array — `TOOL_INVOCATION` then `COMPLETE` — so the
tool runs inline and the run completes; the tool result is stored under `tool.weather.lookup` in the
run context. The agent opts in by listing `TOOL_INVOCATION` in its `supportedCommands`.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl framework-examples/tools-http -am verify
```

`verify` runs the deterministic test, which asserts the tool was invoked and the run reached
`COMPLETED`. To watch it print, run `HttpToolExample.main` from your IDE.

## Expected behaviour / output

The workflow invokes the HTTP tool against the in-process loopback server and reaches
`WorkflowStatus.COMPLETED`. `main` prints, for example:

```text
Workflow 'tools-http-demo' (run <id>) finished with status: COMPLETED
Tool result (tool.weather.lookup): {"city":"London","summary":"Sunny","tempC":18}
```

The bundled test asserts the terminal status and that the tool result is present in context.

## Files to read first

1. `src/main/java/.../HttpToolExample.java` — the loopback server, the `HttpToolProvider` wiring, and
   the scripted `TOOL_INVOCATION` → `COMPLETE` flow.
2. `src/main/resources/workflows/tools-http-demo.workflow/workflow.json` — the one-step workflow.
3. `src/main/resources/agents/tools-http-agent.agent/agent.json` — the agent that opts into
   `TOOL_INVOCATION` and routes to the `fake` provider.
4. `src/test/java/.../HttpToolExampleTest.java` — the deterministic assertion.

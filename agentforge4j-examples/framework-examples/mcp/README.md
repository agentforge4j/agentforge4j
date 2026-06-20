# MCP

## What this teaches

How to expose tools to a workflow through the Model Context Protocol (MCP), so an agent can use
capabilities served by an MCP server. The example stays deterministic and offline by providing its
own in-process transport instead of a live external server — no subprocess, no network, no keys.

## AgentForge4j capability demonstrated

The `agentforge4j-mcp` provider, wired via `AgentForge4jBootstrap.withToolProviders(...)`. The
example implements the framework-owned `McpTransport` interface with a small stub that exposes one
tool and returns a fixed result; the rest of the wiring — `McpServerConnection` and `McpToolProvider`
— is identical to a real deployment, where only the transport (`StdioTransport` /
`StreamableHttpTransport`) differs. The agent's single scripted response is `TOOL_INVOCATION` then
`COMPLETE`; the tool result is stored under `tool.echo` in the run context.

## How to run

From the examples root (`agentforge4j-examples/`), after installing the framework into your local
`.m2` (`./mvnw install -DskipTests` in the OSS reactor):

```bash
./mvnw -pl framework-examples/mcp -am verify
```

`verify` runs the deterministic test, which asserts the MCP tool was invoked and the run reached
`COMPLETED`. To watch it print, run `McpToolExample.main` from your IDE.

## Expected behaviour / output

The workflow calls the MCP tool over the in-process transport and reaches `WorkflowStatus.COMPLETED`.
`main` prints, for example:

```text
Workflow 'mcp-demo' (run <id>) finished with status: COMPLETED
Tool result (tool.echo): {"echoed":"hello from MCP"}
```

The bundled test asserts the terminal status and that the tool result is present in context.

## Files to read first

1. `src/main/java/.../McpToolExample.java` — the `McpToolProvider` / `McpServerConnection` wiring and
   the scripted `TOOL_INVOCATION` → `COMPLETE` flow.
2. `src/main/java/.../StubMcpTransport.java` — the in-process `McpTransport` that makes the run
   offline and deterministic.
3. `src/main/resources/agents/mcp-agent.agent/agent.json` — the agent that opts into
   `TOOL_INVOCATION` and routes to the `fake` provider.
4. `src/test/java/.../McpToolExampleTest.java` — the deterministic assertion.

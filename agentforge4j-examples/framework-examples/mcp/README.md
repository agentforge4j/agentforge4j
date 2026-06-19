# MCP

> **Status: implementation pending (Batch 5).** This README describes the intended example; the
> Maven module and sources — or a documented deferral — land in Batch 5.

## What this teaches

How to expose tools to a workflow through the Model Context Protocol (MCP), so an agent can use
capabilities served by an MCP server. The example stays deterministic and dependency-free, using an
in-process or scripted transport rather than a live external server.

## AgentForge4j capability demonstrated

The `agentforge4j-mcp` module: wiring an MCP tool provider into a workflow, driven by the shipped
deterministic fake LLM provider. Batch 5 grounding decides between a real in-process round-trip and
a scripted transport; if neither is reachable through public API, this example ships as a documented
deferral explaining the wiring.

## How to run

```bash
./mvnw -pl framework-examples/mcp -am verify
```

(plus the `main()` entry point, once implemented).

## Expected behaviour / output

The workflow obtains a tool over MCP and reaches its terminal `WorkflowStatus`; the bundled test
asserts the round-trip and final status deterministically.

## Files to read first

*Pending implementation (Batch 5).*

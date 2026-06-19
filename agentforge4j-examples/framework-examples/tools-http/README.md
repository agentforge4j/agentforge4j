# HTTP Tools

> **Status: implementation pending (Batch 4).** This README describes the intended example; the
> Maven module and sources land in Batch 4.

## What this teaches

How to give an agent an HTTP-backed tool — the workflow calls out over HTTP to perform an action
and uses the response. The example talks to a loopback HTTP stub so it stays deterministic and
needs no external service.

## AgentForge4j capability demonstrated

The `agentforge4j-tools-http` module: defining and invoking an HTTP tool, driven by the shipped
deterministic fake LLM provider. (The exact tool-definition surface is confirmed against live
source in Batch 4.)

## How to run

```bash
./mvnw -pl framework-examples/tools-http -am verify
```

(plus the `main()` entry point, once implemented).

## Expected behaviour / output

The workflow invokes the HTTP tool against the in-process loopback stub and reaches its terminal
`WorkflowStatus`; the bundled test asserts the tool was called and the final status deterministically.

## Files to read first

*Pending implementation (Batch 4).*

# Quick Start

> **Status: implementation pending (Batch 2).** This README describes the intended example; the
> Maven module and sources land in Batch 2.

## What this teaches

The shortest path from nothing to a running workflow: assemble an AgentForge4j runtime in plain
Java, start a workflow, and observe it reach its terminal state — no Spring, no real LLM keys, no
network.

## AgentForge4j capability demonstrated

Framework-agnostic bootstrap (`AgentForge4jBootstrap.defaults().build()`) and running a workflow to
completion, driven by the shipped deterministic fake LLM provider so the run is reproducible.

## How to run

```bash
./mvnw -pl framework-examples/quick-start -am verify
```

(plus the `main()` entry point, once implemented).

## Expected behaviour / output

The workflow runs end to end and reports a terminal `WorkflowStatus`; the bundled test asserts that
status deterministically.

## Files to read first

*Pending implementation (Batch 2).*

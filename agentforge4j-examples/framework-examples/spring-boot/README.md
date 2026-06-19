# Spring Boot

> **Status: implementation pending (Batch 3).** This README describes the intended example; the
> Maven module and sources land in Batch 3.

## What this teaches

How to adopt AgentForge4j in a Spring Boot application using the starter — auto-configuration wires
the runtime so you can inject it and run a workflow with no manual assembly.

## AgentForge4j capability demonstrated

The `agentforge4j-spring-boot-starter`: auto-configured runtime beans, driven by the shipped
deterministic fake LLM provider for a reproducible run.

## How to run

```bash
./mvnw -pl framework-examples/spring-boot -am verify
```

(plus the Spring Boot application entry point, once implemented).

## Expected behaviour / output

The application starts, runs a workflow to a terminal `WorkflowStatus`, and the bundled test
asserts that status deterministically.

## Files to read first

*Pending implementation (Batch 3).*

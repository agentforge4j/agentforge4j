# AgentForge4j Examples

Standalone, runnable examples for [AgentForge4j](https://github.com/agentforge4j). Each example
consumes the **published** `org.agentforge4j:*` artifacts exactly the way a third-party application
would — there is no reach into the framework's internal modules. The examples are deliberately
small: the code stays minimal and the per-example README does the teaching.

These examples are Open Source under **Apache-2.0** and build **independently of the AgentForge4j
reactor** (this tree is not a module of the OSS build).

---

## Categories

| Category | What it covers |
|---|---|
| `framework-examples` | Using the framework directly from Java — bootstrap and run a workflow, suspend/resume for human approval, the Spring Boot starter, HTTP tools, and MCP. |
| `workflow-language-examples` | The declarative workflow language — branching, loops, SPAR, inputs, approvals, retries, resources, and agent signals. *(Reserved; lands in a later batch.)* |
| `workflow-catalog-examples` | The shipped catalog of ready-made workflows. *(Reserved; lands in a later batch.)* |

---

## Framework examples

| Example | What it teaches |
|---|---|
| [`quick-start`](framework-examples/quick-start) | Assemble the framework and run a workflow to completion — the shortest runnable program. |
| [`human-approval`](framework-examples/human-approval) | Suspend a run at a `HUMAN_APPROVAL` gate, then approve or reject it. |
| [`spring-boot`](framework-examples/spring-boot) | Use the Spring Boot starter to auto-configure the framework and run a workflow from a `CommandLineRunner`. |
| [`tools-http`](framework-examples/tools-http) | Give an agent an HTTP-backed tool, served by the example's own loopback endpoint. |
| [`mcp`](framework-examples/mcp) | Expose a tool to an agent over the Model Context Protocol, using an in-process transport. |

---

## Prerequisite — install the framework into your local repository

Until AgentForge4j publishes `0.1.0` to Maven Central, the examples resolve
`org.agentforge4j:*:0.0.1-SNAPSHOT` from your local `~/.m2`. Build and install the framework once
before building the examples:

```bash
# from a checkout of the agentforge4j (OSS) repository:
./mvnw install -DskipTests
```

---

## Build everything

From this directory:

```bash
./mvnw verify
```

That compiles every example, runs its tests, and enforces the SPDX license header. The Maven
Wrapper is bundled, so a global Maven install is not required.

To build a single example:

```bash
./mvnw -pl framework-examples/<example> -am verify
```

---

## Contributing

New examples follow the existing shape: one tiny runnable entry point, one deterministic test that
asserts the workflow reaches its terminal state (no real LLM keys, no network), and a README using
the five-section format the framework examples already use. Every Java source carries the
`// SPDX-License-Identifier: Apache-2.0` header.

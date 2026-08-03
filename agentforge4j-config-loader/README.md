# agentforge4j-config-loader

Loads workflow, agent, and integration definitions — from the filesystem or from shipped classpath
bundles — validates them against the JSON schemas, and turns them into core domain models.

## Why it exists

`core` defines the domain model but deliberately knows nothing about where definitions come from or
how they are stored on disk. Something has to read the external JSON/markdown bundles, validate them
before they become live `WorkflowDefinition`/`AgentDefinition` objects, and offer in-memory
repositories the runtime can query. That is this module's whole job: the bridge between authored
configuration and the typed contracts in `core`.

## How it fits

`agentforge4j-config-loader` depends on [`agentforge4j-core`](../agentforge4j-core/README.md),
[`agentforge4j-util`](../agentforge4j-util/README.md), and
[`agentforge4j-schema`](../agentforge4j-schema/README.md) (for the validation contracts). It locates
shipped bundles on the classpath at the `shipped-workflows` / `shipped-agents` roots that a
separately shipped, independently-versioned workflow catalog provides, without depending on that
catalog module at compile time. It uses Jackson and `networknt`'s JSON Schema validator (pinned to
the 2.0.x / Jackson-2 line) to parse and validate. The [`agentforge4j-bootstrap`](../agentforge4j-bootstrap/README.md) facade and the
Spring Boot starter drive it; applications rarely call it directly.

## Key public types

| Type | Purpose |
|---|---|
| `AgentForgeLoader` | Facade that loads agents and workflows from a directory and/or the shipped classpath bundles. |
| `AgentLoader` / `WorkflowDirectoryLoader` | The loader contracts, with classpath and filesystem implementations (`ClasspathAgentLoader`, `FileSystemAgentLoader`, `ClasspathWorkflowLoader`, `FileSystemWorkflowLoader`). |
| `InMemoryAgentRepository` / `InMemoryWorkflowRepository` | Default in-memory `AgentRepository` / `WorkflowRepository` implementations populated from loaded definitions. |
| `AgentPromptResolver` | Resolves an agent's system-prompt content, with a filesystem-backed default. |
| `FileSystemIntegrationConfigLoader` | Loads `integration.json` files and validates them against the integration schema. |
| `WorkflowValidator` / `ValidationReport` | Validation entry point and its structured error report. |

## Public configuration

This module reads no environment variables or properties of its own — directories and load toggles
are supplied by the caller (the bootstrap facade or the Spring starter).

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-config-loader</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.config.loader;
```

Exports `com.agentforge4j.config.loader` and its `.agent`, `.catalog`, `.workflow`, `.integration`,
`.prompt`, `.repository`, and `.validation` sub-packages.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).

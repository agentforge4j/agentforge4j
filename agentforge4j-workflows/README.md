# agentforge4j-workflows

The shipped workflow and agent bundles that ride along on the classpath, plus the locators that
resolve them.

## Why it exists

AgentForge4j ships a set of ready-to-run workflows and agents so an application can do something
useful out of the box without authoring any definitions first. Those bundles are classpath
resources, and consumers need a reliable way to enumerate and open them by id. This module owns
exactly that: the resource bundles and the two locator utilities that map an id to its resources.
It holds no execution logic and no filesystem access — purely shipped assets and their classpath
addressing.

## How it fits

`agentforge4j-workflows` depends only on [`agentforge4j-util`](../agentforge4j-util/README.md). It
is consumed by [`agentforge4j-config-loader`](../agentforge4j-config-loader/README.md), whose
classpath loaders use these locators to read the shipped bundles into core domain models.

## Key public types

| Type | Purpose |
|---|---|
| `WorkflowBundleLocator` | Enumerates shipped workflow ids and resolves a workflow's JSON and bundle resources on the classpath. |
| `AgentBundleLocator` | Enumerates shipped agent ids and resolves an agent's JSON, system prompt, and optional boundaries prompt. |

Shipped workflows live under `/shipped-workflows/` and agents under `/shipped-agents/`, each as a
bundle directory addressed by id.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-workflows</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.workflows;
```

Exports the single package `com.agentforge4j.workflows`.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).

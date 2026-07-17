# agentforge4j-schema

The JSON Schema assets that define the on-disk shape of AgentForge4j workflow, agent, blueprint,
artifact, and integration bundles, plus a small provider for loading them from the classpath.

## Why it exists

Workflow and agent definitions live outside Java as JSON. Those documents need a single, shared
contract so the loaders can validate user-authored bundles before they ever reach the core domain
model. This module owns that contract — the schema files, the accessor that surfaces them, and the
framework version/compatibility-gate metadata the shipped workflow catalog is checked against. It
carries no validation engine of its own; the loader modules feed these schemas to a validator.
Keeping the schemas in one low-level module lets both the loaders and any external tooling (editors,
CI checks) validate against exactly the same definitions.

## How it fits

`agentforge4j-schema` depends only on [`agentforge4j-util`](../agentforge4j-util/README.md). It is
consumed by [`agentforge4j-config-loader`](../agentforge4j-config-loader/README.md), which validates
loaded JSON against these schemas. It has no third-party runtime dependencies — it is schema assets
and an accessor.

## Key public types

| Type | Purpose |
|---|---|
| `SchemaProvider` | Contract exposing the five bundled schemas: `workflowSchema()`, `agentSchema()`, `blueprintSchema()`, `artifactSchema()`, `integrationSchema()`. |
| `ClasspathSchemaProvider` | Default `SchemaProvider` that loads the schema resources from the classpath, fail-fast if any is missing. |
| `FrameworkVersion` | Exposes the running framework version, read from a build-time resource; the catalog compatibility gate compares a shipped catalog's declared version bounds against it. |
| `WorkflowSchemaVersion` | The workflow schema version this framework build understands (`SUPPORTED_WORKFLOW_SCHEMA_VERSION`), enforced against each workflow document's declared `schemaVersion`. |

The bundled schemas are `workflow.schema.json`, `agent.schema.json`, `blueprint.schema.json`,
`artifact.schema.json`, and `integration.schema.json`.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-schema</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.schema;
```

Exports `com.agentforge4j.schema` and opens the `schema` resource location for reflective loading.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).

# agentforge4j-workflows-catalog

The shipped workflow and agent **catalog** — the built-in workflows (and their bundled agents) as
classpath resources under `/shipped-workflows/` and `/shipped-agents/`, plus a compatibility
manifest. **Resources-only**: no production Java. The framework owns the loader and the
compatibility gate; this module owns only content.

## Independently versioned

This module carries its **own `<version>`** (`0.1.0-SNAPSHOT`), decoupled from the framework's
reactor version, so the catalog can be released on its own cadence — a shipped-workflow change does
not force a framework version bump. The version graduates to `1.0.0` when the redesigned catalog
becomes the intended public release.

Consumers pin the catalog version explicitly (it is not `${project.version}`):

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-workflows-catalog</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Loading model

The framework discovers the catalog on the classpath: `agentforge4j-config-loader`'s
`WorkflowBundleLocator` / `AgentBundleLocator` resolve `/shipped-workflows/` and `/shipped-agents/`
via `ClassLoader.getResource`, and `AgentForgeLoader.loadWorkflowsFromClasspath` runs the
`CatalogCompatibilityGate` before loading.

- **A pure framework with no catalog jar on the classpath loads zero shipped workflows** — a valid,
  deliberate empty default, not an error.
- When a catalog **is** present, its `/shipped-workflows/agentforge4j-catalog.json` manifest is
  required: it declares the framework version range it supports
  (`minimumAgentForge4jVersion` / optional `maximumAgentForge4jVersion`) and the
  `workflowSchemaVersion`. An incompatible or missing manifest fails fast.
- **Exactly one catalog jar is supported** — a second collides on the single `/shipped-workflows/index`.

## Verification

The module verifies the **real** catalog in-reactor through the testkit (test scope): conformance
(every shipped workflow owns a colocated verification scenario), data-driven scenario execution of
each shipped workflow, scenario-schema conformance, manifest compatibility, and a resources-only
artifact check (no `.class`, no `Class-Path`, `Automatic-Module-Name: agentforge4j.workflows.catalog`).

> Note: this module is resources-only and has no `module-info` (an automatic module); it is consumed
> on the class path or module path via `ClassLoader.getResource`, which resolves the hyphenated,
> non-encapsulated `/shipped-workflows/` and `/shipped-agents/` roots across both layouts.

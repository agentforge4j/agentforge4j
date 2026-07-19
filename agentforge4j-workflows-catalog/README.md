# agentforge4j-workflows-catalog

The shipped workflow and agent **catalog** — the built-in workflows (and their bundled agents) as
classpath resources under `/shipped-workflows/` and `/shipped-agents/`, plus a compatibility
manifest. **Resources-only**: no production Java. The framework owns the loader and the
compatibility gate; this module owns only content.

## Independently versioned

This module carries its **own `<version>`** (`0.1.0`), decoupled from the framework's reactor
version, so the catalog can be released on its own cadence — a shipped-workflow change does not
force a framework version bump. Each catalog release pins to exactly one framework version via
its compatibility manifest (see below); catalog 0.1.0 requires framework 0.1.0.

Consumers pin the catalog version explicitly (it is not `${project.version}`):

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-workflows-catalog</artifactId>
  <version>0.1.0</version>
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

## Adding a shipped workflow

Discovery of verification scenarios is registry-free, but shipping the workflow itself is
**index-driven** — two hand-maintained files are load-bearing, and the conformance gate turns
omissions into loud failures:

1. Create `src/main/resources/shipped-workflows/<id>.workflow/` with a `workflow.json` whose `id`
   equals the folder name and declares the supported `schemaVersion`.
2. Add `<id>` as a line in `shipped-workflows/index` (the list the production loader drives).
3. List every additional bundle entry (blueprints, artifacts, `agents/<x>.agent` folders, step
   prompts) in the bundle's own `<id>.workflow/index`.
4. Add at least one `verification/<scenario>/` folder with `script.json`, `README.md`, and an
   `expected-result.json` that names `<id>` and asserts at least the run's final `status`
   (assertion-free scenarios are rejected by the conformance gate).
5. Add a bundle-level `README.md` describing the workflow and its scenarios.
6. Keep every `.json`/`.md` resource free of monetary/billing vocabulary — the whole catalog is
   scanned, including "never do X" phrasing.

No Java, pom, CI, or test-class changes are needed: the scenario factory, schema validation,
conformance gate, and forbidden-term scan pick the new folder up automatically.

> Local caveat: Maven copies changed resources into `target/classes` on incremental builds but
> never deletes removed ones — after **deleting or renaming** catalog files, run with `clean` or
> the stale copy keeps tests green locally (CI always builds clean).

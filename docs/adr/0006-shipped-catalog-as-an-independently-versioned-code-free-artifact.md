# ADR-0006: Shipped workflow catalog as an independently versioned, code-free artifact

## Status

Accepted (records the removal of the earlier `agentforge4j-workflows` module, which this model supersedes)

## Date

2026-06 (approximate — the catalog split shipped across June 2026; recorded retrospectively 2026-07-09)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction: the catalog split, the code-free rule, and the ServiceLoader validator seam were designed and merged before this record was authored.

## Context

Shipped workflows are content, not framework: they evolve on a different cadence, are reviewed by different criteria, and should be consumable (or omitted) independently of the engine. The earlier model — workflow bundles living in a plain framework module — coupled content changes to framework releases and invited Java code to creep into what should be portable data. Some workflows genuinely need executable support (e.g. artifact validators); that code must not travel inside a content artifact.

## Decision

1. Shipped workflows and agents live in `agentforge4j-workflows-catalog`, an **independently versioned** artifact within the reactor.
2. The artifact is **code-free**: JSON/markdown resources only. The framework loads zero shipped workflows when the artifact is absent.
3. Executable support a workflow requires (validators and similar) is **generic framework capability**, discovered via `ServiceLoader` — never bundled in the catalog artifact, and never workflow-specific: no per-workflow Java classes, validators, or test classes exist anywhere in the framework tree or the catalog module.
4. Normative conventions bind every bundle: verification scenarios with deterministic expected results, and a runnable example project per shipped workflow.

The pre-split `agentforge4j-workflows` module is removed; this artifact model supersedes it.

## Alternatives considered

- **Workflows compiled into the core** (always present). Simplest distribution, but couples content cadence to framework releases and makes "install the engine without opinionated content" impossible.
- **Per-workflow Maven modules with Java.** Invites per-workflow code (validators, tests) into content, multiplying module and JPMS overhead and eroding the data-not-code principle (ADR-0001).
- **Separate content repository.** Cleanest separation on paper, but adds discovery and compatibility-gating friction disproportionate to the project's current stage; independent versioning inside the reactor captures the benefit now without foreclosing extraction later.

## Consequences

### Positive

- Catalog content releases on its own version line; consumers pin content and framework independently, mediated by a compatibility gate.
- A fresh framework install is unopinionated; adding the artifact is plug-and-play.
- The code-free rule keeps every shipped workflow reviewable as data.

### Negative

- Publishing needs dedicated release wiring (its own release job and POM flattening so the published artifact stands alone).
- The ServiceLoader seam means a workflow needing new executable support requires a coordinated framework change — and that support must be designed as a generic, reusable capability, never as code specific to the workflow that motivated it.

### Neutral / tradeoffs

- Whether the catalog is populated at any given moment is a content-release question, not a framework question: this record defines the artifact model, not the content inventory (see ADR-0007 for the content decision).

## Compatibility impact

The catalog artifact's coordinates, its manifest, and the compatibility gate against the framework version are public contracts. The code-free rule is a normative constraint on all future shipped content. Users consuming the framework without the artifact are unaffected.

## Implementation notes

`agentforge4j-workflows-catalog` with its own version line; empty `shipped-workflows/index` and `shipped-agents/index` on `main @ 9ad289dd` (2026-07-09); validator discovery via `ServiceLoader`. A stale comment in the module POM predates the content removal and does not reflect current state.

## Follow-up work

- Catalog release wiring (dedicated deploy job + POM flattening) — still open at the time of writing.
- First rebuilt content bundles: at the time of writing the catalog on `main` ships structure only (empty indexes — the legacy content was deliberately removed, see ADR-0007) and rebuilt content is pending in open pull requests, with two initial bundles expected. Nothing in this record should be read as "the catalog is populated" until those merge.

## Related documents

- ADR-0007 (greenfield catalog rebuild — the content decision this artifact model hosts).
- Shipped-workflow catalog conventions (normative rules for bundle contents).

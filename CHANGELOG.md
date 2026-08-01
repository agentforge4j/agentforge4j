# Changelog

All notable changes to the **framework** (the `framework-v*` release track, the Maven reactor
rooted at this file) are documented here. The shipped workflow catalog and the workflow builder
release independently and keep their own changelogs:
[`agentforge4j-workflows-catalog/CHANGELOG.md`](agentforge4j-workflows-catalog/CHANGELOG.md) and
[`agentforge4j-workflow-builder/CHANGELOG.md`](agentforge4j-workflow-builder/CHANGELOG.md).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **`PREMIUM` capability tier.** A fourth `ModelTier` above `POWERFUL`, with a shipped default on
  every built-in provider — that provider's strongest available model, which is the same model as
  `POWERFUL` wherever no distinct higher-capability one is configured. Operators retarget it per
  provider like any other tier, through `agentforge4j.llm.model-tiers.<provider>.premium`.
  The tier is declarable in configuration as well as resolvable at runtime: the shipped
  `agent.schema.json` and `workflow.schema.json` accept `"modelTier": "PREMIUM"` on an agent
  definition and as a step-level override, alongside the three tiers that were already valid.

### Changed

- **Invalid-tier error messages list the tier set from the enum.** Bootstrap configuration
  parsing, agent invocation, and Spring auto-configuration previously each restated
  `LITE, STANDARD, POWERFUL` in their own message; all three now derive the list from
  `ModelTier`, so a tier added later cannot go missing from one of them.

### Deprecated

### Removed

### Fixed

- **Search snippets for the published API reference.** Page descriptions on the hosted Javadoc are
  now kept inside the length a search result actually shows, so they are no longer cut off
  mid-word. Descriptions built around a generated type name are shortened at a word boundary when
  the name is long; the page's own title still carries the name in full. The reference's landing
  page is also reachable from each surface's overview, so the MCP and Spring Boot starter surfaces
  can be found from the entry point rather than only by direct link.

### Security

## [0.1.0] - 2026-07-19

First public open-source release.

### Added

- **Framework introduction.** AgentForge4j: an embeddable Java framework for governed AI
  workflows, defined in external configuration (JSON + markdown) and executed faithfully by a
  runtime that never improvises step order.
- **Workflow engine and runtime.** A domain model (`WorkflowDefinition`, `AgentDefinition`,
  `StepDefinition`, `WorkflowState`) and execution engine driving explicit step behaviours
  (AGENT, SPAR, WORKFLOW, INPUT, RESOURCE, BRANCH, FAIL, RETRY_PREVIOUS, VALIDATE,
  ASSIGN_CONTEXT, AGGREGATE), human approval/review/input gates, structured tool execution
  through a `ToolProvider` SPI, and a decision-level audit event log.
- **LLM providers.** A provider-independent `ModelTier` abstraction plus ten
  `ServiceLoader`-discovered provider modules: OpenAI, Anthropic Claude, Google Gemini, Mistral,
  Ollama, vLLM, AWS Bedrock, Azure OpenAI, an OpenAI-compatible generic client, and a
  deterministic fake provider for offline development and testing.
- **Governance and secure defaults.** A fail-closed default `ToolPolicy`, an outbound HTTP egress
  guard rejecting private/loopback/link-local/cloud-metadata targets, a reserved internal context
  namespace no LLM-emitted command can write, and atomically-enforced step retry ceilings.
- **Workflow testing.** A test kit (`agentforge4j-testkit`) and fake LLM client for deterministic,
  scriptable, repeatable workflow tests.
- **Shipped workflow catalog.** `agentforge4j-workflows-catalog` ships the Workflow Execution
  Estimator and Agent Creator workflows.
- **Examples.** A standalone example tree (`agentforge4j-examples`) with 13 runnable examples
  spanning framework assembly, Spring Boot integration, HTTP and MCP tools, one example per
  workflow-language feature, and both shipped catalog workflows.
- **Spring Boot integration.** `agentforge4j-spring-boot-starter` auto-configures the runtime
  behind a single injectable bean.
- **Workflow Builder compatibility.** Compatible with the visual Workflow Builder
  (`@agentforge4j/workflow-builder-react`), released and versioned independently on npm — see its
  own [CHANGELOG](agentforge4j-workflow-builder/CHANGELOG.md).
- **Verification and quality gates.** Static analysis (Checkstyle, SpotBugs), an OWASP
  dependency-vulnerability scan, license-header enforcement, JPMS module-path compilation, an
  aggregate CycloneDX SBOM, and a hosted CI matrix (JDK 17/21, Linux/Windows) with a Sonar quality
  gate, secret scanning, and CodeQL analysis.
- **Documentation.** A generated documentation site built from the framework's own Javadoc and
  Spring configuration metadata, 34 architecture decision records, and per-module READMEs.

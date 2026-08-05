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

- **One shared wire protocol behind the OpenAI-style provider adapters.** The request and response
  shapes that OpenAI, OpenAI-compatible, Azure OpenAI, Mistral and vLLM had each been carrying
  their own copy of now live once, in the exported `com.agentforge4j.llm.wireprotocol` package,
  along with a single message-role enum shared by every provider that names a role on the wire.
  Unknown response fields are tolerated by these types themselves rather than depending on the
  `ObjectMapper` the embedding application supplies, so a provider adding a field cannot break
  parsing on any host. Two behaviour changes ride along: the OpenAI-compatible adapter now omits
  `max_output_tokens` entirely when no output-token budget is requested, instead of sending an
  explicit `null` that strict servers reject; and Mistral now reports cached input tokens when its
  deployment provides them, where it previously always reported none. `LlmHttpErrorBodyTruncate`
  is public, and the 500-character cap it applies to response bodies embedded in exception messages
  now also covers the parse-failure paths of all nine HTTP providers, not just non-2xx responses.
- **Some providers log the full response body at DEBUG.** The Azure OpenAI, Bedrock, Gemini,
  Mistral, OpenAI, OpenAI-compatible and vLLM adapters log the complete response body at `DEBUG`
  before parsing it, so a malformed or unexpected payload can be inspected without reproducing the
  call. Claude and Ollama do not. Operators enabling `DEBUG` on `com.agentforge4j.llm.*` should
  note that this writes model output verbatim to logs; `INFO` and above are unaffected, and
  anything embedded in an exception message or `ERROR` log stays capped at 500 characters. A
  single provider-independent policy for response-body logging is still to be decided.
- **Retry-policy absence is expressed the same way on both LLM contracts — breaking.**
  `LlmClientConfiguration.getRetryPolicy()` now returns a nullable `LlmRetryPolicy` instead of
  `Optional<LlmRetryPolicy>`, matching `LlmClient.getRetryPolicy()`, which already used `null` to
  mean "no policy requested". *Migration:* a configuration that overrides the method changes its
  return type and returns `null` where it returned `Optional.empty()`; callers replace
  `getRetryPolicy().orElse(null)` with `getRetryPolicy()`, and `getRetryPolicy().isPresent()` with
  a `!= null` check. Configurations that never overrode the method need no change. In the same
  pass, `RetryingLlmClient.getRetryPolicy()` reports the policy it was constructed with — the one
  it actually retries with — rather than the wrapped client's own value, which could be `null`
  while retries were genuinely running.
- **Invalid-tier error messages list the tier set from the enum.** Bootstrap configuration
  parsing, agent invocation, and Spring auto-configuration previously each restated
  `LITE, STANDARD, POWERFUL` in their own message; all three now derive the list from
  `ModelTier`, so a tier added later cannot go missing from one of them.

### Deprecated

### Removed

### Fixed

- **Bootstrap `connect.timeout` uses the shared duration grammar.** A provider's connect timeout
  discovered from an environment variable or system property previously accepted ISO-8601 only,
  while the same setting written as provider configuration or a provider option accepted more; all
  three now accept ISO-8601 (`PT30S`), the compact shorthand (`30s`, `500ms`), and a unitless
  amount interpreted as milliseconds (`5000` means five seconds). Duration-typed settings on other
  surfaces keep their own grammar. The bootstrap README documented an
  `AGENTFORGE4J_LLM_<PROVIDER>_CONNECT_TIMEOUT_SECONDS` key that was never read — the key is
  `AGENTFORGE4J_LLM_<PROVIDER>_CONNECT_TIMEOUT`.

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

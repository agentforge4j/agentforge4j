# ADR-0009: ServiceLoader discovery, JPMS module policy, and the bootstrap entry point

## Status

Accepted

## Date

2026-07-09 (retrospective record date — these decisions were made and implemented incrementally across the framework's development; no single decision date exists)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction, consolidating three related decisions: provider discovery via `ServiceLoader`, the JPMS module policy, and the `bootstrap` module as the framework-agnostic assembly entry point.

## Context

An embeddable framework needs a principled answer to three questions: how modules encapsulate (JPMS vs classpath conventions), how optional implementations attach (discovery vs explicit wiring), and what the minimal assembly surface is (so integrations don't each reinvent runtime construction). Answering them inconsistently per module produces exactly the wiring drift the framework exists to prevent in workflows.

## Decision

1. **JPMS throughout, one documented carve-out.** Every reactor module ships `module-info.java` and exports public packages only — except `agentforge4j-spring-boot-starter`, which deliberately has no module descriptor (the auto-configuration ecosystem it integrates with is not module-path-friendly).
2. **Providers are discovered, never declared.** LLM providers register via JPMS `provides ... LlmClientFactory` and are discovered through `ServiceLoader`; there is no classpath-only `META-INF/services` fallback for provider factories. Adding a provider means adding a dependency, not editing wiring.
3. **`bootstrap` is the assembly entry point.** The `bootstrap` module offers framework-agnostic defaults for assembling a runtime and declares **no** concrete providers; the Spring Boot starter depends on `bootstrap`, not on `runtime` directly, so any integration layer builds on the same assembly surface.

## Alternatives considered

- **Classpath-only, `META-INF/services` discovery.** Works everywhere, but forfeits JPMS encapsulation (internals become reachable API) and dual registration paths invite drift between them.
- **Explicit provider wiring in configuration.** Predictable, but every new provider is a wiring change in every embedder; discovery makes provider presence a dependency decision.
- **No dedicated bootstrap; integrations assemble `runtime` directly.** Each integration re-derives default assembly, and defaults drift per integration.
- **JPMS everywhere, no carve-out.** Forcing a module descriptor onto the starter fights its ecosystem for purity's sake; the carve-out is documented rather than hidden.

## Consequences

### Positive

- Public API is exactly the exported packages; internals are unreachable by construction.
- Provider modules are drop-in: presence on the module path is the whole integration.
- One assembly surface: the starter and any future integration layer share `bootstrap` defaults, and `bootstrap`'s provider-free dependency set keeps the core assembly honest.

### Negative

- JPMS raises the contribution bar (module descriptors, `provides` clauses, export discipline) and constrains some tooling.
- `ServiceLoader` discovery is implicit: a missing provider dependency surfaces at resolution time, not compile time.

### Neutral / tradeoffs

- The starter carve-out is a permanent, documented asymmetry — acceptable because it is a leaf integration module, not core surface.

## Compatibility impact

Exported packages define the public API surface; `provides`/`uses` clauses on `LlmClientFactory` (and other SPIs) are part of the extension contract. The starter's absence of a module descriptor is a documented expectation for module-path consumers.

## Implementation notes

`module-info.java` present in every reactor module except the Spring Boot starter (verified); `ServiceLoader` wiring in `bootstrap` (`AgentForge4jBootstrap`, `LlmClientWiring`, `RuntimeAssembler`); providers register via JPMS `provides` with no classpath-only registration for `LlmClientFactory`; `bootstrap` declares no concrete providers; the starter depends on `bootstrap`. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Resolve whether the MCP integration module ships in the first public release: an upstream SDK dependency ships an unusable automatic module name, a compatibility hazard this module policy makes visible (see ADR-0015). This record documents the policy; that inclusion decision is tracked separately and does not block this record.

## Related documents

- ADR-0015 (the MCP integration module the follow-up decision concerns).

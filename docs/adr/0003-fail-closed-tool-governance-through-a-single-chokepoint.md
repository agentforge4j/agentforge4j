# ADR-0003: Fail-closed tool governance through a single execution chokepoint

## Status

Accepted (supersedes the earlier permissive-default tool policy)

## Date

2026-06 (approximate — the fail-closed flip and chokepoint consolidation landed across June 2026; recorded retrospectively 2026-07-09)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction, consolidating several related decisions (default policy, egress classification, secret resolution, approval suspend/resume) that were made and shipped incrementally.

## Context

Tools are where a workflow touches the outside world: processes, networks, files, external servers. The original default policy permitted everything, which made the safe configuration an opt-in — the wrong default for a framework whose value proposition is governed AI workflows. Governance logic scattered per tool provider would enforce inconsistently and give no single audit point. Secrets referenced by tools must never appear inline in portable workflow definitions.

## Decision

1. **Single chokepoint.** Every tool invocation — regardless of provider — flows through one execution service: resolve → validate → policy → timeout, with human-approval suspend/resume handled at the same point.
2. **Fail-closed default policy.** The shipped default (`SecureDefaultToolPolicy`) allows in-process tools and denies remote-network and local-process tools. Broader access requires an explicit opt-in: `allowAll()` or a custom policy. The previous permissive default was deleted, not deprecated.
3. **Egress classification.** Outbound network access is classified and guarded (`HttpEgressGuard` / `OutboundEgressGuard`), giving policies a real risk signal rather than a tool name.
4. **Core-owned secret resolution.** Secrets resolve through a `SecretResolver` SPI owned by the core (environment-variable default); definitions carry references, never values.

## Alternatives considered

- **Permissive default with opt-in restrictions** (the superseded model). Zero-friction first run, but unsafe by default and easy to ship to production unhardened.
- **Per-provider governance.** Each tool source enforces its own rules — inconsistent semantics, duplicated logic, no single audit point, and approvals behave differently per provider.
- **Static allowlists in workflow definitions only.** Reviewable, but blind at runtime: no classification of what a tool actually reaches (network egress, local process), and unenforceable against provider drift.

## Consequences

### Positive

- Secure by default; the dangerous configuration requires a deliberate, visible opt-in.
- One audit point: every invocation, denial, approval, and timeout appears in the same event stream with the same semantics.
- Approval suspend/resume works uniformly across all tool sources, including externally provided ones.

### Negative

- First-run friction: remote tools are denied until the embedder writes or opts into a policy. This is deliberate, but it is a support cost.
- Policy configuration is required reading for any non-trivial deployment.

### Neutral / tradeoffs

- What a tool *can do* is determined by what the provider actually exposes at runtime, not by declarations (see ADR-0004).

## Compatibility impact

`ToolPolicy` and `SecretResolver` are public SPIs. Removing the permissive default was a deliberate pre-1.0 breaking change (see ADR-0013): embedders relying on the old default must now opt in explicitly. Workflow definitions are unaffected except that inline secret values were never and are never supported.

## Implementation notes

Chokepoint: `DefaultToolExecutionService` (`agentforge4j-runtime`). Policy SPI: `core.spi.tool.ToolPolicy`; shipped default `SecureDefaultToolPolicy`; the permissive `NoOpToolPolicy` is absent from the tree. Egress guards in `util.net`. `SecretResolver` in `core.spi.integration`. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Sandboxing for locally spawned tool processes remains future work.

## Related documents

- ADR-0004 (realized tools as the single source of capability truth).
- ADR-0015 (external tool servers under the same governance).

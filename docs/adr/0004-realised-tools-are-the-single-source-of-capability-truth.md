# ADR-0004: Realised tools are the single source of capability truth

## Status

Accepted (supersedes the declared capability envelope)

## Date

2026-06-16 (merge date of the envelope removal; recorded retrospectively 2026-07-09)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction. The declared capability envelope had already been removed from the codebase when this record was authored.

## Context

Tools originally carried two capability descriptions: what an integration *declared* it could do, and what the resolved provider *actually* exposed at runtime. Two sources of truth invite divergence — a declaration can overstate, understate, or simply drift from the provider's real surface, and nothing cross-checked them. Policy and audit decisions made against declarations would then govern a fiction. Externally provided tool sources make this worse: their real tool set is only knowable at resolution time.

## Decision

The set of tools a provider actually exposes at runtime — the realised tools — is the single source of capability truth. The declared capability envelope was removed as a clean break (see ADR-0013): policy evaluation, approval decisions, and audit all operate on realised tools. Where a tool source cannot supply risk metadata, defaults are conservative rather than permissive.

## Alternatives considered

- **Keep both, cross-check at resolution** (declaration validated against realised set). Adds a verification mechanism and a new failure mode to define (what happens on mismatch?) while still maintaining two descriptions of one fact.
- **Declarations as the authority, providers conform.** Unenforceable against external tool sources whose surface the framework does not control.
- **Keep the envelope as documentation-only metadata.** Dead surface that looks authoritative; readers and tooling would keep trusting it.

## Consequences

### Positive

- One truth: what a run could invoke is exactly what governance evaluated and audit recorded.
- External tool sources integrate honestly — their capabilities are discovered, not asserted.
- A whole class of declaration-drift bugs is structurally impossible.

### Negative

- Capability is only knowable after resolution; static workflow-time reasoning about "what could this tool do" is weaker than a (trusted) declaration would allow.
- Removing the envelope was a breaking change for anything that consumed it (accepted under the pre-1.0 policy).

### Neutral / tradeoffs

- Conservative risk defaults for metadata-less tools shift friction onto tool-source authors: supply metadata or be treated as risky.

## Compatibility impact

The declared capability envelope is gone from the public tool descriptor surface; extension authors describe tools through what their provider realises. Workflow definitions are unaffected.

## Implementation notes

No declared-capability type or accessor remains in main sources (verified: zero hits on `main @ 9ad289dd`, 2026-07-09). Realised-tool resolution and conservative risk defaults live in the tool integration layer and flow through the governance chokepoint (ADR-0003).

## Follow-up work

None.

## Related documents

- ADR-0003 (governance chokepoint that consumes realised tools).
- ADR-0015 (external tool servers — where discovery-time truth matters most).

# ADR-0005: Prompt-injection isolation via compile-enforced context provenance

## Status

Accepted

## Date

2026-06-18 (merge date of the compile-enforced provenance change; recorded retrospectively 2026-07-09)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction. The compile-enforced option was chosen over runtime-only alternatives during the original design round; this record documents that choice after the fact.

## Context

Prompt injection is the defining security problem of LLM-driven workflows: untrusted text (user input, tool output, model output) that reaches a prompt can attempt to override instructions. Any mitigation that relies on developers *remembering* to mark input as untrusted will drift — the failure mode is silent and the consequence is instruction override. The framework needed isolation that cannot be skipped by omission.

## Decision

Every context value carries a provenance, and the requirement is enforced at compile time — a value cannot enter the context without its origin being stated. Untrusted input is rendered inside a dedicated untrusted-input envelope in prompts, and system rules are injected through a dedicated provider block that untrusted content cannot occupy. LLM output is always classified as untrusted input.

## Alternatives considered

- **Runtime-only tagging** (annotations, naming conventions, string markers). Bypassable by omission; drift is silent until exploited.
- **Input sanitization/filtering.** Structurally incomplete against injection — there is no reliable filter for adversarial natural language; filtering also mutates legitimate content.
- **Prompt discipline only** (documentation and conventions, no mechanism). The status quo it replaces; unacceptable for a framework whose positioning is governed AI workflows.

## Consequences

### Positive

- The injection surface is explicit and machine-checkable: the compiler rejects provenance-free context writes.
- Prompt assembly separates system rules, trusted context, and untrusted content structurally rather than by convention.
- Treating model output as untrusted closes the reflection loop (model-written text cannot self-promote to trusted instructions).

### Negative

- API friction: every context producer must state provenance, including internal ones where it feels redundant.
- The envelope adds prompt tokens on every step that renders untrusted content.

### Neutral / tradeoffs

- Provenance is assigned at the point of entry; it does not yet propagate automatically through values derived from untrusted inputs. Isolation is per-value, not transitive.

## Compatibility impact

The provenance requirement is part of the public context API: embedders and extension authors must supply provenance when writing context values. Workflow definitions are unaffected. Prompt structure (envelope, system-rules block) is an internal rendering contract.

## Implementation notes

`ContextProvenance` and the untrusted-input envelope in `core.workflow.context`; system-rules injection via `SystemRulesProvider` in the command schema layer. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Taint propagation across derived context values is explicitly deferred future work.

## Related documents

- ADR-0003 (tool governance — tool output enters context as untrusted input).

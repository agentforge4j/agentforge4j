# Architecture Decision Records

This directory holds the framework's architecture decision records (ADRs): short, immutable documents recording the significant design decisions, the alternatives considered, and the consequences accepted. Records follow a common MADR-style structure (Status, Date, Context, Decision, Alternatives considered, Consequences, Compatibility impact, Implementation notes, Follow-up work).

## Conventions

- **Numbering** is sequential and four-digit; a number is never reused. Gaps in the sequence are reserved numbers for decisions still in flight (listed below).
- **Retrospective records are allowed.** A decision that was made and implemented before being recorded carries a "Retrospective note" saying so; its Date is the decision or merge date where history pins one, otherwise the record date.
- **Records are not rewritten.** Once Accepted, a record's decision substance is fixed. A change of direction is a new ADR that supersedes the old one; the old record's Status is updated to note the superseding record, and nothing else in it changes.
- **Statuses:** `Proposed` (decision drafted, not yet in force), `Accepted` (in force), `Superseded` (replaced by a later record).

## Index

| ADR | Title | Status | Date |
|---|---|---|---|
| [0001](0001-portable-workflow-definitions-with-a-deterministic-execution-contract.md) | Portable workflow definitions with a deterministic execution contract | Accepted | 2026-07-09 |
| [0002](0002-the-framework-core-carries-no-identity-or-commercial-concepts.md) | The framework core carries no identity or commercial concepts | Accepted | 2026-05-31 |
| [0003](0003-fail-closed-tool-governance-through-a-single-chokepoint.md) | Fail-closed tool governance through a single execution chokepoint | Accepted | 2026-06 |
| [0004](0004-realised-tools-are-the-single-source-of-capability-truth.md) | Realised tools are the single source of capability truth | Accepted | 2026-06-16 |
| [0005](0005-prompt-injection-isolation-via-compile-enforced-provenance.md) | Prompt-injection isolation via compile-enforced context provenance | Accepted | 2026-06-18 |
| [0006](0006-shipped-catalog-as-an-independently-versioned-code-free-artifact.md) | Shipped workflow catalog as an independently versioned, code-free artifact | Accepted | 2026-06 |
| [0007](0007-greenfield-rebuild-of-the-shipped-workflow-catalog.md) | Greenfield rebuild of the shipped workflow catalog | Accepted | 2026-06-30 |
| [0008](0008-model-tier-abstraction-lite-standard-powerful.md) | Model-tier abstraction (LITE / STANDARD / POWERFUL) | Accepted | 2026-07-09 |
| [0009](0009-serviceloader-discovery-and-jpms-module-policy.md) | ServiceLoader discovery, JPMS module policy, and the bootstrap entry point | Accepted | 2026-07-09 |
| [0010](0010-blueprints-execute-by-file-reference-only.md) | Blueprints execute by file reference only | Accepted | 2026-06-22 |
| [0011](0011-the-runtime-event-contract-is-the-verification-surface.md) | The runtime event contract is the verification surface | Accepted | 2026-06 |
| [0012](0012-pre-execution-interception-and-blocked-run-resumability.md) | Pre-execution interception and blocked-run resumability | Accepted | 2026-07-09 |
| [0013](0013-pre-1-0-clean-break-compatibility-policy.md) | Pre-1.0 clean-break compatibility policy | Accepted | 2026-07-09 |
| [0014](0014-collection-gate-step-behaviour.md) | Collection gate step behaviour | Proposed | 2026-07-10 |
| [0015](0015-mcp-servers-as-governed-tool-sources.md) | MCP servers as first-class governed tool sources | Accepted | 2026-07-09 |
| [0016](0016-deterministic-token-efficiency-governance.md) | Deterministic token-efficiency governance | Proposed | 2026-07-10 |
| [0017](0017-workflow-execution-estimation.md) | Workflow execution estimation | Proposed | 2026-07-10 |
| [0018](0018-release-management-and-publication-sequencing.md) | Release management and publication sequencing | Proposed | 2026-07-10 |
| [0019](0019-workflow-requirements-and-transition-enforcement.md) | Workflow requirements and step-transition enforcement | Accepted | 2026-07-09 |

Reserved numbers with no entry above are decisions still too early to draft; none remain reserved at present.

# Architecture Decision Records

This directory records the architecture decisions that shape AgentForge4j — the reasoning behind how the framework is built, not a running design log. See `TEMPLATE.md` to start a new one.

## Conventions

- **Numbering** is sequential and four-digit; a number is never reused. Gaps in the sequence are reserved numbers for decisions still in flight (see Reserved, below).
- **Retrospective records are allowed.** A decision that was made and implemented before being recorded carries a "Retrospective note" saying so; its Date is the decision or merge date where history pins one, otherwise the record date.
- **Records are not rewritten.** Once Accepted, a record's decision substance is fixed. A change of direction is a new ADR that supersedes the old one; the old record's Status is updated to note the superseding record, and nothing else in it changes. Editorial fixes (typos, broken links, a renamed class in an Implementation-notes pointer) that don't alter what was decided may still be corrected in place.
- **Statuses:** `Proposed` (decision drafted, not yet in force), `Accepted` (in force — verified merged to the default branch, not just "PR open" or "stack ready"), `Superseded` (replaced by a later record), `Deprecated` (no longer recommended, with no single successor), `Rejected` (seriously considered and explicitly declined).

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
| [0020](0020-multi-artifact-monorepo-with-prefixed-release-tracks.md) | Multi-artifact monorepo with prefixed release tracks | Accepted | 2026-07-11 |
| [0021](0021-release-branching-model.md) | Release branching model | Proposed | 2026-07-10 |
| [0022](0022-independent-versioning-and-schemaversion-as-the-compatibility-contract.md) | Independent track versioning, with schemaVersion as the sole compatibility contract | Accepted | 2026-07-11 |
| [0023](0023-required-strict-workflow-schemaversion.md) | Required, strict workflow schemaVersion | Accepted | 2026-07-11 |
| [0024](0024-patch-release-forward-port-flow.md) | Patch release forward-port flow | Proposed | 2026-07-10 |
| [0025](0025-pre-1-0-and-post-1-0-support-policy.md) | Pre-1.0 and post-1.0 support policy | Proposed | 2026-07-10 |
| [0026](0026-tag-triggered-release-ci-and-environment-gated-publishing.md) | Tag-triggered release CI and environment-gated publishing | Accepted | 2026-07-11 |
| [0027](0027-no-release-candidates-pre-1-0-mandatory-rc-soak-for-1-0-0.md) | No release candidates pre-1.0; mandatory RC soak for 1.0.0 | Proposed | 2026-07-10 |
| [0028](0028-workflow-builder-version-continuity-from-0-5-0.md) | Workflow builder version continuity from 0.5.0 | Accepted | 2026-07-11 |
| [0029](0029-one-time-pre-public-retag-of-legacy-builder-tags.md) | One-time pre-public retag of legacy builder tags | Proposed | 2026-07-10 |
| [0030](0030-generated-compatibility-matrix.md) | Generated compatibility matrix | Proposed | 2026-07-10 |
| [0031](0031-per-track-changelogs-and-committed-release-note-sources.md) | Per-track changelogs and committed release-note sources | Accepted | 2026-07-11 |
| [0032](0032-catalog-compatibility-as-an-exact-pinned-framework-version.md) | Catalog compatibility as an exact pinned framework version | Accepted | 2026-07-11 |
| [0033](0033-numericversion-qualifier-awareness-for-release-candidates.md) | NumericVersion becomes qualifier-aware, as a hard prerequisite for 1.0.0-RC1 | Proposed | 2026-07-10 |

## Reserved

Numbers claimed for planned ADRs not yet drafted. A reservation exists only once merged
here on the default branch — an open PR proposing a row does not itself reserve the
number. Each entry needs an owner and a date; a reservation with neither is
indistinguishable from an abandoned one.

| # | Topic | Reserved by | Date reserved | Reference |
|---|---|---|---|---|

Nothing currently reserved. New decisions are allocated the next sequential number.

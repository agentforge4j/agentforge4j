# ADR-0020: Multi-artifact monorepo with prefixed release tracks

## Status

Accepted

## Date

2026-07-11 (retrospective record date — the decision was ruled 2026-07-10 as part of the release
management strategy; the implementing workflows merged the same day)

## Retrospective note

This ADR is written after `tag-guard.yml` and the three track-specific release workflows
(`release-framework.yml`, `release-catalog.yml`, `release-builder.yml`) had already merged to
`main`. It records the decision those workflows implement.

## Context

This repository holds three publishable artifacts with different natural release cadences and
different publish targets: the framework (a Maven reactor, to Central), the shipped workflow
catalog (an independently versioned Maven module, to Central), and the workflow builder (an npm
package). ADR-0018 already established that these publish as three independent tracks and that a
release is a prefixed Git tag; this ADR is the standalone, detailed record of that tagging and
tracking scheme, since ADR-0018's own scope is the one-time launch sequencing, not the ongoing
tag/track mechanics.

## Decision

Three independently versioned release tracks share this one repository, distinguished only by
tag prefix — never by an unprefixed tag:

- `framework-v<version>` — the Maven reactor rooted at this repository, to Maven Central.
- `catalog-v<version>` — `agentforge4j-workflows-catalog`, to Maven Central with a flattened,
  parent-free POM (`flatten-maven-plugin`, `agentforge4j-workflows-catalog/pom.xml`).
- `builder-v<version>` — `agentforge4j-workflow-builder`, to npm as
  `@agentforge4j/workflow-builder-react`.

Tag format is `<track>-v<major>.<minor>.<patch>[-RC<n>]`, enforced by `tag-guard.yml` on every
tag push regardless of track, plus each track's own release workflow re-checking its own prefix.
**Plain, unprefixed `v*` tags are forbidden outright** — `tag-guard.yml` fails any tag that does
not match `^(framework|builder|catalog)-v\d+\.\d+\.\d+(-RC\d+)?$`. Extraction to a separate repo
per track is an explicit future option, not exercised now, revisited only after all of: the
`schemaVersion` wire format is stable, the project is post-1.0, and a concrete driver exists
(external frontend contributors, licensing divergence, `.org` build isolation). Prefixed tags are
chosen partly because they make an eventual `git filter-repo` extraction carry over 1:1.

## Alternatives considered

- **Separate repositories per artifact from day one.** Rejected pre-1.0: three repos to keep in
  sync for a solo maintainer is pure overhead before there is any external contributor pressure
  to split.
- **Single reactor version for everything** (catalog and builder version-locked to the
  framework). Rejected — couples catalog content iteration and builder UI iteration to the
  framework's release cadence for no technical reason; see ADR-0006 for the catalog's
  independent-versioning rationale specifically.

## Consequences

### Positive

- Each track ships on its own cadence with zero cross-track release blocking.
- A single `tag-guard.yml` closes the whole "wrong prefix" failure class for all three tracks at
  once, independent of which track-specific workflow would otherwise have silently ignored a
  malformed tag.
- Extraction later is a mechanical `git filter-repo`, not a history rewrite, because tags already
  carry the track boundary.

### Negative

- A contributor unfamiliar with the convention can be confused by three version numbers in one
  repository; mitigated by CONTRIBUTING.md's release-tracks table and the issue template's
  component field.

### Neutral / tradeoffs

- The monorepo's benefit (one PR review pipeline, one CI configuration) is a pre-1.0-only
  convenience the extraction trigger list exists to revisit later.

## Compatibility impact

None on runtime APIs. Repository structure and CI-workflow-visible convention only.

## Implementation notes

`tag-guard.yml` (repository root `.github/workflows/`) enforces the shared prefix regex on every
tag push. `release-framework.yml`, `release-catalog.yml`, `release-builder.yml` each additionally
re-check their own exact prefix in their `release-guard` job. `agentforge4j-workflows-catalog/
pom.xml` carries `flatten-maven-plugin` so its published POM has no `<parent>` reference to the
framework's reactor version.

## Follow-up work

Author the extraction trigger list as a tracked, checkable set of conditions if and when any one
of the three preconditions (stable schemaVersion, post-1.0, concrete driver) is met.

## Related documents

- ADR-0018 — release management and publication sequencing (the original decision to use
  independently versioned, prefixed tracks; this ADR gives it a standalone, detailed treatment).
- ADR-0006 — shipped catalog as an independently versioned, code-free artifact.
- ADR-0026 — tag-triggered release CI and environment-gated publishing.
- `CONTRIBUTING.md` — Releases section (contributor-facing summary of this scheme).

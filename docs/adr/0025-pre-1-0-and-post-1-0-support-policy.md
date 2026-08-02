# ADR-0025: Pre-1.0 and post-1.0 support policy

## Status

Proposed

## Date

2026-07-10

## Context

Three independently versioned tracks (ADR-0020), each releasing on its own cadence, need an
explicit answer to "which version gets a fix" — without one, every bug report implicitly asks
whether an old minor is still owed a patch, and a solo maintainer has no way to bound that
commitment before it accumulates.

## Decision

**Pre-1.0 (public commitment, published verbatim in README, SECURITY.md, the builder README, and
the docs site):**

> Only the latest minor of each track receives patches. When 0.(N+1).0 ships on a track, 0.N.x
> of that track is end-of-life. No LTS, no backports. Breaking changes may occur in any 0.x minor
> and are always listed in the release notes. Builder versions <0.5.0 are unsupported pre-launch
> test builds.

**Post-1.0:**

- The latest minor of the latest major per track receives full support.
- On a major version bump, the previous major's last minor receives critical/security fixes only
  for a 6-month window, then reaches end-of-life. One overlap window at a time — never more than
  one legacy major supported simultaneously.
- The `schemaVersion` migration window (ADR-0022/ADR-0023) governs wire-format compatibility
  separately from this support-window policy.

## Alternatives considered

- **LTS lines pre-1.0.** Rejected — a solo-maintainer pre-1.0 project has no realistic capacity
  to backport fixes to multiple simultaneously-supported lines; stating "no LTS" up front avoids
  an implicit promise nobody can keep.
- **No stated policy at all** (support whatever, informally, on request). Rejected — leaves every
  bug report's triage ambiguous and gives contributors no way to know whether reporting against
  an old version is even useful.
- **Indefinite post-1.0 legacy-major support.** Rejected — an unbounded support tail is the
  standard trap that turns into permanent maintenance debt; a fixed 6-month window forces
  migration without leaving users stranded immediately at a major bump.

## Consequences

### Positive

- One published policy answers "is my version still supported" for every track without needing
  to ask.
- The issue template's component field (ADR-0020) plus this policy gives a mechanical triage
  rule: report against the latest release of the relevant track, or expect "please upgrade" as
  the first response.

### Negative

- Users who cannot upgrade immediately at a pre-1.0 minor bump get no backport — an accepted cost
  of the "no LTS pre-1.0" rule, stated plainly so it is not a surprise.

### Neutral / tradeoffs

- The post-1.0 policy is written now but cannot be exercised for months — its correctness rests
  on the pre-1.0 clean-break policy (ADR-0013) actually ending cleanly at 1.0, which ADR-0013
  itself flags as a required follow-up.

## Open questions

None in the policy's wording. What remains open is only whether it will need revision once a
real end-of-life actually happens and the process is exercised for the first time.

## Compatibility impact

None on runtime APIs. Support-commitment policy only.

## Verification note

Becomes Accepted once the pre-1.0 half of this policy has actually been exercised at least once
— a track has shipped a 0.(N+1).0 minor and 0.N.x was correctly treated as end-of-life (no patch
issued against it). Currently no track has shipped even a first release, so the policy is stated
but unexercised. The published-verbatim locations (README, SECURITY.md, builder README, docs
site) are live in SECURITY.md and the builder README as of this record; README and docs-site
placement track their own workstreams.

## Related documents

- ADR-0013 — pre-1.0 clean-break compatibility policy (the policy this one hands off to at 1.0).
- ADR-0021 — release branching model.
- `SECURITY.md` — the published policy text.

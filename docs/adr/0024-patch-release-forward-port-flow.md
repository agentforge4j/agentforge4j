# ADR-0024: Patch release forward-port flow

## Status

Proposed

## Date

2026-07-10

## Context

A fix applied to a released version's maintenance branch (ADR-0021) has to reach `main` somehow,
or every subsequent release re-ships the same bug. The two obvious mechanisms — a direct
cherry-pick, or requiring the fix be reimplemented independently on each branch — both carry real
risk: an unreviewed cherry-pick can silently reintroduce a bug `main` had already moved past, and
mandatory independent reimplementation doubles the chance the two fixes diverge in behavior.

## Decision

A patch fix is applied on the `release/<track>-0.N.x` branch first. It then reaches `main`
through a **reviewed forward-port pull request** — never a direct or unreviewed cherry-pick:

- When the release branch and `main` have not diverged in the affected area, the forward-port PR
  reuses the fix commit as-is.
- When they have diverged, the forward-port PR adapts the fix in-PR to `main`'s current shape.
- Either way, the forward-port PR carries equivalent regression coverage on both sides — the test
  that proves the release-branch fix must also prove the `main`-side version.
- The release stays open (not considered fully shipped) until the forward-port PR merges.

## Alternatives considered

- **Direct/unreviewed cherry-pick onto `main`.** Rejected (RM-9): skips review on the exact
  commit that's about to land on the development line, and a cherry-pick that doesn't cleanly
  apply gets silently adapted with no forcing function to actually check the adaptation.
- **Independent reimplementation required on each branch.** Rejected: doubles the maintenance
  branch's divergence risk from `main` for every single patch, for no benefit over adapting the
  same fix in a reviewed PR.
- **Merge the release branch into `main` wholesale.** Rejected: brings unrelated release-branch-
  only history into `main`'s line, when only the specific fix should travel.

## Consequences

### Positive

- Every change reaching `main` — including patches — goes through the same review gate.
- Equivalent regression coverage on both sides means a future release-branch fix and its
  `main`-side counterpart are provably testing the same thing, not just visually similar diffs.

### Negative

- More process per patch than a bare cherry-pick — deliberate, to avoid silent divergence.

### Neutral / tradeoffs

- "Release stays open until forward-port merges" means a patch's user-visible availability (the
  published artifact) and its "fully done" status (also on `main`) can be temporarily out of
  sync — acceptable, since the artifact is what users consume immediately.

## Open questions

None in the flow itself. What's unverified is only that the flow has actually been exercised —
see Verification note.

## Compatibility impact

None on runtime APIs. Process only.

## Verification note

Becomes Accepted once a real patch has gone through this exact flow — fix on a
`release/<track>-0.N.x` branch, patch tag released, forward-port PR reviewed and merged to
`main`. No patch has happened yet, because no track has shipped a release.

## Related documents

- ADR-0021 — release branching model (the maintenance branches this flow operates on).
- ADR-0020 — multi-artifact monorepo with prefixed release tracks.

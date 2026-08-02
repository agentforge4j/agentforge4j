# ADR-0029: One-time pre-public retag of legacy builder tags

## Status

Proposed

## Date

2026-07-10

## Context

Three lightweight, unprefixed tags already exist on `origin` from before the prefixed-track
scheme (ADR-0020) was decided: `v0.2.1` and `v0.2.2` (the same commit) and `v0.3.0` — all builder
versions, all already published to npm as unsupported pre-launch test builds. ADR-0020 forbids
unprefixed tags outright, and the permanent policy (ADR-0031, and this ADR's own scope) is that
tags are never deleted once the repository is public. These three tags predate that permanent
policy taking effect and sit in direct conflict with it.

## Decision

**One-time, pre-public exception**: recreate each of the three legacy tags as an annotated,
prefixed tag at the same commit, then delete the original unprefixed tag, both locally and on
`origin`:

```
git tag -a builder-v0.2.1 -m "builder 0.2.1 (pre-launch test build)" v0.2.1^{}
git tag -d v0.2.1 && git push origin :refs/tags/v0.2.1
git push origin builder-v0.2.1
```

(repeated for `v0.2.2` and `v0.3.0`). This is deliberate deletion-plus-recreation, not a rename —
kept as **annotated** tags (unlike the originals, which are lightweight) specifically because
they anchor provenance for already-published npm versions, even though those versions are
unsupported. This is a one-time migration exception to the "tags are never deleted" rule below,
justified only because the repository has zero GitHub Releases referencing these tags and is not
yet public — no external consumer can be depending on the unprefixed tag names existing.

**Permanent policy, effective from this point on**: tags are never deleted after the repository
is public. A bad release is fixed by a new patch tag (ADR-0024), never by deleting or moving the
original.

## Alternatives considered

- **Leave the legacy tags as-is, unprefixed, alongside the new prefixed scheme.** Rejected —
  directly contradicts ADR-0020's "plain `v*` tags are forbidden outright," and `tag-guard.yml`
  would have no way to distinguish "grandfathered" from "actually malformed" without a permanent
  carve-out that undermines the guard's own simplicity.
- **Leave them as lightweight tags after adding the prefix** (tag both names, keep both). Rejected
  — leaves two tags pointing at the same commit indefinitely, which is exactly the ambiguity
  `tag-guard.yml` exists to prevent going forward.

## Consequences

### Positive

- Closes the only known pre-existing violation of ADR-0020's tag format before CI enforcement
  (`tag-guard.yml`) is enabled — nothing on `origin` would fail the guard retroactively.
- Provenance for the three already-published npm versions is preserved under the correct prefix,
  not simply lost.

### Negative

- Anyone who had already referenced `v0.2.1`/`v0.2.2`/`v0.3.0` by tag name (rather than by commit
  or npm version) loses that reference — accepted, since the repository is not yet public and no
  external consumer can plausibly be depending on it.

### Neutral / tradeoffs

- This is explicitly a one-time exception, not a precedent — the "never delete" rule takes over
  immediately after, with no other carve-out.

## Open questions

None in the plan itself. What remains open is only execution timing — this is migration
close-out work, sequenced before CI enablement (`tag-guard.yml` going live) per the launch
timeline.

## Compatibility impact

None on runtime APIs. Git tag naming only; affects provenance lookup for three already-published,
already-unsupported npm versions.

## Verification note

Becomes Accepted once the three tags have actually been recreated and deleted as described —
confirmed not yet done as of this record: `v0.2.1`, `v0.2.2`, `v0.3.0` still exist unprefixed on
`origin`, no `builder-v0.2.1`/`builder-v0.2.2`/`builder-v0.3.0` tags exist yet.

## Related documents

- ADR-0020 — multi-artifact monorepo with prefixed release tracks (the format these tags must
  conform to, and the "never delete after public" policy this ADR's exception precedes).
- ADR-0028 — workflow builder version continuity from 0.5.0 (these three tags are exactly the
  "published versions below 0.5.0" that ADR retroactively declares unsupported).

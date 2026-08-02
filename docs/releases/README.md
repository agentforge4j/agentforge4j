# Release notes and the maintainer checklist

AgentForge4j publishes three independently versioned tracks — the framework (`framework-v*`,
to Maven Central), the shipped workflow catalog (`catalog-v*`, to Maven Central), and the
workflow builder (`builder-v*`, to npm). Each track has its own release-note sources and its own
tag prefix; releasing one track never blocks another.

## Release-note sources

Every tagged release is preceded by a committed release-note source at:

```
docs/releases/<track>/<version>.md
```

for example `docs/releases/framework/0.1.0.md`, `docs/releases/catalog/0.1.0.md`,
`docs/releases/builder/0.5.0.md`. The file is committed to the tagged tree **before** the tag is
pushed — the release CI guard fails the release if it is missing. The tagged CI's `release-announce`
stage publishes this file verbatim as the body of the GitHub Release (title = tag). It is also the
canonical in-repo record, alongside the track's `CHANGELOG.md`.

### Mandatory headings template

Every release-note source must contain all five of these headings, in this order (the release
guard checks each one is present via a case-insensitive heading match; it does not check content,
only that the section exists):

```markdown
# <track> <version>

## Highlights

<What's new or changed, for a reader deciding whether to upgrade.>

## Breaking changes

<Anything that requires consumer action, or "None.">

## Compatibility

<For the framework: the workflow schemaVersion(s) this release supports. For the catalog: the
minimum/maximum framework version this release's manifest declares. For the builder: the
schemaVersion(s) it can export/import, and the npm dist-tag this release publishes to
(`latest` or `next`).>

## Coordinates

<Maven groupId:artifactId:version, or the npm package name and version.>

## Support

<Per the pre-1.0 support policy: this is the only supported minor of this track until the next
minor ships. See [SECURITY.md](../../SECURITY.md).>
```

## Maintainer checklist — framework minor

1. Confirm `main` is Sonar-green (`quality-gate` check) and every wave PR intended for this
   release has merged.
2. Commit `docs/releases/framework/0.N.0.md` (the template above) and the framework's
   `CHANGELOG.md` entry — **before tagging**.
3. `mvn versions:set -DnewVersion=0.N.0` and commit the release-preparation change.
4. Push an annotated tag `framework-v0.N.0` on that commit, then **immediately** commit and push
   `0.(N+1).0-SNAPSHOT` back onto `main` so the development line never sits at a released version
   number.
5. Watch `release-framework.yml`; approve the `maven-central` environment once `release-verify`
   is green.
6. Confirm the artifact resolves from Central (a fresh `-Dmaven.repo.local` smoke resolve).
7. Confirm `release-announce` created the GitHub Release (body = the committed note source; the
   aggregate CycloneDX SBOM is attached).
8. Regenerate the compatibility matrix / `release.json` and deploy the docs site if either
   changed.
9. Delete `release/framework-0.(N-1).x` once it is two minors old.

## Maintainer checklist — catalog or builder minor

Same shape as the framework checklist (guard → note source + changelog → version bump → tag →
watch CI → verify → announce), scoped to that track's own `release-<track>.yml` workflow and
`docs/releases/<track>/<version>.md`. The builder release additionally follows this sequence:
publish to npm → verify the `latest` dist-tag moved (or `next`, for a release candidate) → bump
its downstream consumer packages → deploy and verify those consumers → deprecate any npm version
below the current minor.

## Patch releases

Fix on the `release/<track>-0.N.x` maintenance branch, following the framework checklist's steps
1–5 scoped to the patch tag, plus regenerating the compatibility matrix. The release stays open
until the fix has also landed on `main` through a reviewed forward-port pull request — patches are
never cherry-picked directly and unreviewed.

## Support policy

Only the latest minor of each track is supported before 1.0.0. See
[SECURITY.md](../../SECURITY.md) for the full policy and how to report a vulnerability, and
[CONTRIBUTING.md](../../CONTRIBUTING.md) for how release tracks and tags work.

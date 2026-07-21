// SPDX-License-Identifier: Apache-2.0
//
// Version-pinned Javadoc surfaces (design §7/§12). Every versioned docs snapshot links its API
// references at `/javadoc/<version>/…` (pinned at cut time by the de-materialiser) — including
// snapshots that have since been ARCHIVED, whose frozen pages are carried forward forever, so their
// Javadoc links must be too. The deploy therefore publishes one frozen Javadoc surface per version
// that is either ACTIVE (versions.json) or ARCHIVED (a directory under archive/) — see
// `javadocBuildVersions` below. Each surface is built from the version's RELEASE TAG — never from
// `main` — so it documents exactly the API that shipped:
//
//   for each version in versions.json UNION the archive/ directory listing (order not significant):
//     1. check out the release tag `v<version>` into an isolated, gitignored source tree
//        (a detached git worktree under .release-staging/);
//     2. install that tag's reactor into an ISOLATED local Maven repository (never the shared
//        ~/.m2 — parallel builds against different sources corrupt shared snapshots);
//     3. run that tag's own scripts/build-javadoc.mjs (the surface builder as it existed at the
//        release, so surface layout changes never break older versions);
//     4. collect the stitched surface into .release-staging/javadoc-versions/<version>/ for the
//        Pages assembly (assemble-site.mjs) to mount at /javadoc/<version>/.
//
// Pre-`0.1.0` versions.json is absent/empty, so this is a no-op — the wiring is inert until the
// first release exists. The per-version build is injectable (options.builder) so the orchestration
// is unit-testable without Maven or tags; the default builder does the real thing. The tag
// checkout/cleanup lifecycle (`withTagWorktree`) — the part with real failure modes: a missing tag,
// or a worktree leaking after a failed build — is proven for real against a manufactured release tag
// by `npm run docs:javadoc-versions-scratch`. The Maven install + the tag's own build-javadoc.mjs
// step is exercised for real only by an actual release deploy.
//
// Run via `npm run javadoc:versions` (usually from the deploy workflow, before assemble-site).

import {execFileSync} from 'node:child_process';
import {cpSync, mkdirSync, readdirSync, readFileSync, rmSync} from 'node:fs';
import {join} from 'node:path';
import {
  MODULE_ROOT,
  STAGING_ROOT,
  VERSIONS_JSON,
  pathExists,
  validateVersion,
} from './release-paths.mjs';
import {ARCHIVE_ROOT} from './archive-transition.mjs';

const REPO_ROOT = join(MODULE_ROOT, '..');
/** Where the per-version surfaces land for assemble-site.mjs. */
export const JAVADOC_VERSIONS_OUT = join(STAGING_ROOT, 'javadoc-versions');
// Release-tag naming: the framework track tags releases as `framework-v<version>` (see
// release-framework.yml / tag-guard.yml), e.g. version `1.2.0` is tagged `framework-v1.2.0`.
export function releaseTag(version) {
  return `framework-v${version}`;
}

const SRC_ROOT = join(STAGING_ROOT, 'javadoc-src');
// The isolated repository path travels via MAVEN_OPTS, which every Maven launcher splits on
// whitespace — a path containing spaces cannot be expressed there portably. CI checkout paths are
// space-free; a local run from a space-containing checkout overrides this with AF4J_JAVADOC_M2
// (buildFromTag fails fast rather than silently corrupting the shared ~/.m2).
const ISOLATED_M2 = process.env.AF4J_JAVADOC_M2 || join(STAGING_ROOT, 'javadoc-m2');

function git(args, opts = {}) {
  return execFileSync('git', args, {cwd: REPO_ROOT, encoding: 'utf8', ...opts});
}

/**
 * Compose MAVEN_OPTS with an isolated `-Dmaven.repo.local`, guaranteed to win. `-D` flags are
 * last-wins on the JVM command line, so simply appending the inherited MAVEN_OPTS after ours would
 * let an inherited `-Dmaven.repo.local` (e.g. a developer's own override for the shared ~/.m2
 * clobbering issue) silently beat the isolation this script exists to guarantee — so any inherited
 * occurrence is stripped before ours is added. Exported so the ordering guarantee is unit-testable.
 *
 * Splits only at whitespace that precedes a new flag (`\s+(?=-)`), not at every space: a flag's
 * VALUE can itself contain spaces (e.g. an inherited `-Dmaven.repo.local=C:\Users\dev\my repo\.m2`
 * on Windows — this repo's own checkout path has one), and naive whitespace splitting would strip
 * only the flag's leading fragment, leaving the rest of the value behind as a stray non-flag token
 * that corrupts the composed MAVEN_OPTS.
 *
 * @param {string|undefined} inheritedMavenOpts process.env.MAVEN_OPTS
 * @param {string} isolatedRepoPath
 * @returns {string}
 */
export function isolatedMavenOpts(inheritedMavenOpts, isolatedRepoPath) {
  const rest = (inheritedMavenOpts || '')
    .split(/\s+(?=-)/)
    .map((flag) => flag.trim())
    .filter((flag) => flag !== '' && !flag.startsWith('-Dmaven.repo.local='));
  return [`-Dmaven.repo.local=${isolatedRepoPath}`, ...rest].join(' ');
}

/**
 * Check out `refs/tags/<releaseTag(version)>` into an isolated detached worktree and run `fn` against
 * its path, guaranteeing the worktree is removed afterwards — even if `fn` throws. This is the part
 * of the per-version build with real failure modes (a missing tag; a worktree leaking after a failed
 * build), so it is factored out to be independently testable against a real tag without requiring a
 * full Maven install (see `npm run docs:javadoc-versions-scratch`).
 *
 * @param {string} version
 * @param {(srcDir: string) => void} fn
 */
export function withTagWorktree(version, fn) {
  validateVersion(version);
  const tag = releaseTag(version);
  const srcDir = join(SRC_ROOT, version);
  git(['rev-parse', '--verify', `refs/tags/${tag}`]); // fail fast: the release tag must exist
  rmSync(srcDir, {recursive: true, force: true});
  // Clears any worktree registration left behind by a run that was killed before its own `finally`
  // (below) could run `git worktree remove` — otherwise `git worktree add` fails hard on a
  // registered-but-missing worktree, blocking every future run until someone prunes by hand.
  git(['worktree', 'prune']);
  git(['worktree', 'add', '--detach', srcDir, tag], {stdio: 'inherit'});
  try {
    fn(srcDir);
  } finally {
    git(['worktree', 'remove', '--force', srcDir], {stdio: 'inherit'});
  }
}

/** The real per-version builder: detached-worktree checkout of the tag, isolated install, surface build. */
function buildFromTag(version, outDir) {
  if (/\s/.test(ISOLATED_M2)) {
    throw new Error(
      `javadoc-versions: the isolated Maven repository path contains whitespace (${ISOLATED_M2}) — ` +
        'MAVEN_OPTS cannot carry it. Set AF4J_JAVADOC_M2 to a space-free directory and rerun.',
    );
  }
  withTagWorktree(version, (srcDir) => {
    // Isolated local repository: the tag's snapshot artifacts must never overwrite the shared
    // ~/.m2. MAVEN_OPTS (a JVM system property, honored by EVERY Maven version and inherited by
    // the tag's own build-javadoc.mjs child, which invokes plain `mvn`) — not MAVEN_ARGS, which
    // only Maven >= 3.9 reads and would silently fall back to the shared repository.
    const env = {...process.env, MAVEN_OPTS: isolatedMavenOpts(process.env.MAVEN_OPTS, ISOLATED_M2)};
    // The wrapper script is platform-specific: `mvnw.cmd` on Windows, `./mvnw` elsewhere. Windows
    // batch files cannot be spawned directly and need a shell; POSIX spawns the wrapper directly —
    // no shell, no argument re-interpretation (same convention as build-javadoc.mjs's `run()`).
    // Explicitly relative-pathed (`.\mvnw.cmd`), not bare (`mvnw.cmd`): cmd.exe (invoked via
    // `shell: true` below) does not search the current directory for an executable unless the
    // path is explicitly relative — a bare `mvnw.cmd` silently resolves against PATH only and
    // fails with "not recognized" even though the wrapper sits right in `srcDir` (same gap already
    // fixed in build-assembled-site.mjs's own MVNW constant).
    const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';
    execFileSync(mvnw, ['-B', '-q', '-DskipTests', '-Dmaven.test.skip=true', 'install'], {
      cwd: srcDir,
      stdio: 'inherit',
      shell: process.platform === 'win32',
      env,
    });
    // The tag's own surface builder, so the surface matches what that release shipped.
    execFileSync(process.execPath, [join('scripts', 'build-javadoc.mjs')], {
      cwd: join(srcDir, 'agentforge4j-docs'),
      stdio: 'inherit',
      env,
    });
    const built = join(srcDir, 'agentforge4j-docs-javadoc', 'build-javadoc', 'next');
    if (!pathExists(join(built, 'index.html'))) {
      throw new Error(`javadoc-versions: the ${releaseTag(version)} build produced no surface at ${built}`);
    }
    cpSync(built, outDir, {recursive: true});
  });
}

/**
 * Build the frozen Javadoc surface for every active released version.
 *
 * @param {string[]} versions versions to build a surface for (active + archived; see
 *        `javadocBuildVersions`) — ordering only affects log/build order, not correctness
 * @param {{builder?: (version: string, outDir: string) => void, outRoot?: string}} [options]
 *        `builder` replaces the tag-sourced build (tests); `outRoot` overrides the output root
 * @returns {string[]} the versions built
 */
export function buildJavadocVersions(versions, options = {}) {
  if (!Array.isArray(versions)) {
    throw new Error('javadoc-versions: `versions` must be an array');
  }
  const outRoot = options.outRoot || JAVADOC_VERSIONS_OUT;
  const builder = options.builder || buildFromTag;

  rmSync(outRoot, {recursive: true, force: true});
  if (versions.length === 0) {
    console.log('[javadoc-versions] no released versions — nothing to build (pre-first-release).');
    return [];
  }
  for (const version of versions) {
    validateVersion(version);
    const outDir = join(outRoot, version);
    mkdirSync(outDir, {recursive: true});
    console.log(`[javadoc-versions] building /javadoc/${version}/ from ${releaseTag(version)}`);
    builder(version, outDir);
  }
  console.log(`[javadoc-versions] built ${versions.length} version-pinned surface(s) at ${outRoot}`);
  return [...versions];
}

/**
 * The full set of versions whose Javadoc surface must be published: every ACTIVE released version
 * (versions.json) plus every ARCHIVED version still carried forward (a directory under archive/).
 * Archiving removes a version from versions.json, but archive-transition.mjs deliberately keeps its
 * versioned_docs snapshot in-repo as provenance specifically so it keeps being served — this mirrors
 * that same "archive/<v>/ exists -> keep serving it forever" rule assemble-site.mjs already applies
 * to the docs archive, applied here to Javadoc. Pure — unit-tested without a real archive/ directory.
 *
 * @param {string[]} activeVersions versions.json contents (newest first)
 * @param {string[]} archivedVersionNames directory names under archive/ (order-independent)
 * @returns {string[]} activeVersions followed by any archived version not already active
 */
export function javadocBuildVersions(activeVersions, archivedVersionNames) {
  const archivedOnly = archivedVersionNames.filter((version) => !activeVersions.includes(version));
  return [...activeVersions, ...archivedOnly];
}

// CLI entry.
if (process.argv[1]?.endsWith('build-javadoc-versions.mjs')) {
  const active = pathExists(VERSIONS_JSON) ? JSON.parse(readFileSync(VERSIONS_JSON, 'utf8')) : [];
  const archivedVersionNames = pathExists(ARCHIVE_ROOT)
    ? readdirSync(ARCHIVE_ROOT, {withFileTypes: true})
        .filter((entry) => entry.isDirectory())
        .map((entry) => entry.name)
    : [];
  buildJavadocVersions(javadocBuildVersions(active, archivedVersionNames));
}

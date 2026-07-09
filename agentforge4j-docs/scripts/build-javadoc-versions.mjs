// SPDX-License-Identifier: Apache-2.0
//
// Version-pinned Javadoc surfaces (design §7/§12). Every versioned docs snapshot links its API
// references at `/javadoc/<version>/…` (pinned at cut time by the de-materialiser), so the deploy
// must publish one frozen Javadoc surface per ACTIVE released version. Each surface is built from
// the version's RELEASE TAG — never from `main` — so it documents exactly the API that shipped:
//
//   for each version in versions.json (newest first):
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
// is unit-testable without Maven or tags; the default builder does the real thing.
//
// Run via `npm run javadoc:versions` (usually from the deploy workflow, before assemble-site).

import {execFileSync} from 'node:child_process';
import {cpSync, mkdirSync, readFileSync, rmSync} from 'node:fs';
import {join} from 'node:path';
import {
  MODULE_ROOT,
  STAGING_ROOT,
  VERSIONS_JSON,
  pathExists,
  validateVersion,
} from './release-paths.mjs';

const REPO_ROOT = join(MODULE_ROOT, '..');
/** Where the per-version surfaces land for assemble-site.mjs. */
export const JAVADOC_VERSIONS_OUT = join(STAGING_ROOT, 'javadoc-versions');
// Release-tag naming: version `1.2.0` is tagged `v1.2.0`. (No tag exists pre-0.1.0; the first
// release fixes this convention — adjust here if the runbook chooses differently.)
export function releaseTag(version) {
  return `v${version}`;
}

const SRC_ROOT = join(STAGING_ROOT, 'javadoc-src');
const ISOLATED_M2 = join(STAGING_ROOT, 'javadoc-m2');

function git(args, opts = {}) {
  return execFileSync('git', args, {cwd: REPO_ROOT, encoding: 'utf8', ...opts});
}

/** The real per-version builder: detached-worktree checkout of the tag, isolated install, surface build. */
function buildFromTag(version, outDir) {
  const tag = releaseTag(version);
  const srcDir = join(SRC_ROOT, version);
  git(['rev-parse', '--verify', `refs/tags/${tag}`]); // fail fast: the release tag must exist
  rmSync(srcDir, {recursive: true, force: true});
  git(['worktree', 'add', '--detach', srcDir, tag], {stdio: 'inherit'});
  try {
    // Isolated local repository: the tag's snapshot artifacts must never overwrite the shared
    // ~/.m2. MAVEN_OPTS (a JVM system property, honored by EVERY Maven version and inherited by
    // the tag's own build-javadoc.mjs child, which invokes plain `mvn`) — not MAVEN_ARGS, which
    // only Maven >= 3.9 reads and would silently fall back to the shared repository.
    const mavenOpts = [`-Dmaven.repo.local=${ISOLATED_M2}`, process.env.MAVEN_OPTS]
      .filter(Boolean)
      .join(' ');
    const env = {...process.env, MAVEN_OPTS: mavenOpts};
    // The wrapper script is platform-specific: `mvnw.cmd` on Windows, `./mvnw` elsewhere.
    const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
    execFileSync(mvnw, ['-B', '-q', '-DskipTests', '-Dmaven.test.skip=true', 'install'], {
      cwd: srcDir,
      stdio: 'inherit',
      shell: true,
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
      throw new Error(`javadoc-versions: the ${tag} build produced no surface at ${built}`);
    }
    cpSync(built, outDir, {recursive: true});
  } finally {
    git(['worktree', 'remove', '--force', srcDir], {stdio: 'inherit'});
  }
}

/**
 * Build the frozen Javadoc surface for every active released version.
 *
 * @param {string[]} versions released versions, newest first (versions.json)
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

// CLI entry.
if (process.argv[1]?.endsWith('build-javadoc-versions.mjs')) {
  const versions = pathExists(VERSIONS_JSON) ? JSON.parse(readFileSync(VERSIONS_JSON, 'utf8')) : [];
  buildJavadocVersions(versions);
}

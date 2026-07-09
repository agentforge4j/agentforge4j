// SPDX-License-Identifier: Apache-2.0
//
// Scratch-cut simulation (design §12, Phase 5a; owner lock: no real release before 0.1.0).
//
// Proves the release-staging + versioning mechanism end-to-end against the real `docs/` tree, then
// fully reverts so nothing throwaway is ever committed:
//
//   1. record the exact pre-state (versions.json bytes, whether the versioned_* dirs exist, git status);
//   2. stage + cut a scratch version;
//   3. ASSERT the snapshot is materialised — no surviving live `file=`/`vocab:`/`javadoc:` directive
//      (fence-aware: documentation *about* the directives inside fenced code blocks is not a live
//      directive), Javadoc links pinned to the scratch version, example code frozen inline;
//   4. BUILD the site with the scratch version present and assert the post-first-release routing
//      actually engaged — the released version served at `/<version>/`, the root and `/latest`
//      redirect pages emitted targeting it, `next` still served. This is the only place the
//      post-release branch of the redirect toggle can be exercised before a real release exists;
//      without it, "no code change is needed at release time" is an untested claim.
//   5. delete every produced artifact (including the scratch build output) and restore the
//      pre-state byte-for-byte;
//   6. assert `git status` is unchanged from step 1 (net-zero: the simulation left no residue).
//
// Steps 5–6 run in `finally`, so a failed assertion still reverts. Run via `npm run docs:scratch-cut`.

import {execFileSync} from 'node:child_process';
import {readFileSync, rmSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {stage} from './release-stage.mjs';
import {cut} from './release-cut.mjs';
import {findLiveDirectives} from './dematerialize.mjs';
import {
  BUILD_DIR,
  DOCUSAURUS_BIN,
  MODULE_ROOT,
  STAGING_ROOT,
  VERSIONS_JSON,
  VERSIONED_DOCS,
  VERSIONED_SIDEBARS,
  listFiles,
  pathExists,
  validateVersion,
} from './release-paths.mjs';

const REPO_ROOT = join(MODULE_ROOT, '..');
const SCRATCH_VERSION = validateVersion(process.argv[2] || '0.0.0-scratch');

function gitStatus() {
  return execFileSync('git', ['status', '--porcelain'], {cwd: REPO_ROOT, encoding: 'utf8'});
}

/** Read a file's bytes, or null if absent. */
function readOrNull(path) {
  return pathExists(path) ? readFileSync(path) : null;
}

/**
 * Assert the cut snapshot contains only materialised content, pinned to the scratch version.
 *
 * Fence-aware: live directives are found on the same MDX AST the de-materialiser rewrites, so a
 * fenced code block that *documents* the directive syntax (e.g. the contributor guide's authoring
 * examples) does not false-fail the cut — it is exactly the content the de-materialiser is
 * guaranteed to leave alone.
 */
function assertMaterialised(snapshotDir, version) {
  const files = listFiles(snapshotDir).filter((f) => f.endsWith('.mdx') || f.endsWith('.md'));
  if (files.length === 0) {
    throw new Error(`scratch-cut: snapshot ${snapshotDir} has no docs`);
  }
  let sawPinnedJavadoc = false;
  for (const rel of files) {
    const text = readFileSync(join(snapshotDir, rel), 'utf8');
    const live = findLiveDirectives(text);
    if (live.length > 0) {
      const listing = live
        .map((d) => `  line ${d.line} [${d.type}]: ${d.excerpt}`)
        .join('\n');
      throw new Error(`scratch-cut: snapshot ${rel} still has live directive(s):\n${listing}`);
    }
    if (text.includes(`/javadoc/${version}/`)) {
      sawPinnedJavadoc = true;
    }
  }
  if (!sawPinnedJavadoc) {
    throw new Error(`scratch-cut: no Javadoc link was pinned to /javadoc/${version}/ — de-materialisation may not have run`);
  }
  console.log(`[scratch-cut] snapshot verified materialised: ${files.length} page(s), Javadoc pinned to ${version}`);
}

/**
 * Build the site with the scratch version present and assert the post-first-release routing engaged:
 * the released version at `/<version>/`, the root and `/latest` redirect pages both emitted
 * targeting it, and the current (`next`) docs still served. Proves design §3's toggle end-to-end —
 * the config's post-release branch is otherwise unreachable until a real release exists.
 */
function assertPostReleaseBuild(version) {
  console.log('[scratch-cut] building the site with the scratch version present (post-release routing proof)');
  execFileSync(process.execPath, [DOCUSAURUS_BIN, 'build'], {cwd: MODULE_ROOT, stdio: 'inherit'});

  const expectations = [
    [join(BUILD_DIR, version, 'index.html'), `the released version served at /${version}/`],
    [join(BUILD_DIR, 'next', 'index.html'), 'the current (next) docs still served'],
    [join(BUILD_DIR, 'index.html'), 'the root redirect page'],
    [join(BUILD_DIR, 'latest', 'index.html'), 'the /latest redirect page'],
  ];
  for (const [path, what] of expectations) {
    if (!pathExists(path)) {
      throw new Error(`scratch-cut: post-release build proof failed — missing ${what} (${path})`);
    }
  }
  for (const page of ['index.html', join('latest', 'index.html')]) {
    const redirect = readFileSync(join(BUILD_DIR, page), 'utf8');
    if (!redirect.includes(`/docs/${version}/`)) {
      throw new Error(`scratch-cut: the ${page} redirect page does not target /docs/${version}/`);
    }
  }
  console.log(`[scratch-cut] post-release routing proven: / and /latest -> /${version}/, next still served`);
}

function main() {
  const snapshotDir = join(VERSIONED_DOCS, `version-${SCRATCH_VERSION}`);
  if (pathExists(snapshotDir)) {
    throw new Error(`scratch-cut: version-${SCRATCH_VERSION} already exists — refusing to run`);
  }

  // 1. Pre-state.
  const preStatus = gitStatus();
  const preVersionsJson = readOrNull(VERSIONS_JSON);
  const preVersionedDocsExisted = pathExists(VERSIONED_DOCS);
  const preVersionedSidebarsExisted = pathExists(VERSIONED_SIDEBARS);
  const sidebarFile = join(VERSIONED_SIDEBARS, `version-${SCRATCH_VERSION}-sidebars.json`);

  try {
    // 2. Stage + cut.
    stage(SCRATCH_VERSION);
    cut(SCRATCH_VERSION);

    // 3. Prove the snapshot is materialised.
    assertMaterialised(snapshotDir, SCRATCH_VERSION);

    // 4. Prove the post-first-release routing against the scratch version.
    assertPostReleaseBuild(SCRATCH_VERSION);
    console.log('[scratch-cut] mechanism proven.');
  } finally {
    // 5. Full revert to the recorded pre-state. The build output is a scratch artifact now (it was
    // built with the scratch version routing) — remove it entirely; `npm run build` recreates it.
    rmSync(BUILD_DIR, {recursive: true, force: true});
    rmSync(snapshotDir, {recursive: true, force: true});
    rmSync(sidebarFile, {force: true});
    if (!preVersionedDocsExisted) {
      rmSync(VERSIONED_DOCS, {recursive: true, force: true});
    }
    if (!preVersionedSidebarsExisted) {
      rmSync(VERSIONED_SIDEBARS, {recursive: true, force: true});
    }
    if (preVersionsJson === null) {
      rmSync(VERSIONS_JSON, {force: true}); // did not pre-exist → delete entirely
    } else {
      writeFileSync(VERSIONS_JSON, preVersionsJson); // restore exact original bytes
    }
    rmSync(STAGING_ROOT, {recursive: true, force: true});
  }

  // 6. Net-zero assertion: the simulation left no residue.
  const postStatus = gitStatus();
  if (postStatus !== preStatus) {
    throw new Error(
      'scratch-cut: git status changed after revert — the simulation left residue:\n' +
        `--- before ---\n${preStatus}\n--- after ---\n${postStatus}`,
    );
  }
  console.log('[scratch-cut] reverted cleanly — git status unchanged.');
}

main();

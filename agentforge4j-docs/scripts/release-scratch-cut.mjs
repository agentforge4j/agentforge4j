// SPDX-License-Identifier: Apache-2.0
//
// Scratch-cut simulation (design §12, Phase 5a; owner lock: no real release before 0.1.0).
//
// Proves the release-staging + versioning mechanism end-to-end against the real `docs/` tree, then
// fully reverts so nothing throwaway is ever committed:
//
//   1. record the exact pre-state (versions.json bytes, whether the versioned_* dirs exist, git status);
//   2. stage + cut a scratch version;
//   3. ASSERT the snapshot is materialised — no surviving `file=`/`vocab:`/`javadoc:` directive, Javadoc
//      links pinned to the scratch version (never `/javadoc/next/`), example code frozen inline;
//   4. delete every produced artifact and restore the pre-state byte-for-byte;
//   5. assert `git status` is unchanged from step 1 (net-zero: the simulation left no residue).
//
// Steps 4–5 run in `finally`, so a failed assertion still reverts. Run via `npm run docs:scratch-cut`.

import {execFileSync} from 'node:child_process';
import {readFileSync, rmSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {stage} from './release-stage.mjs';
import {cut} from './release-cut.mjs';
import {
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

/** Assert the cut snapshot contains only materialised content, pinned to the scratch version. */
function assertMaterialised(snapshotDir, version) {
  const files = listFiles(snapshotDir).filter((f) => f.endsWith('.mdx') || f.endsWith('.md'));
  if (files.length === 0) {
    throw new Error(`scratch-cut: snapshot ${snapshotDir} has no docs`);
  }
  let sawPinnedJavadoc = false;
  for (const rel of files) {
    const text = readFileSync(join(snapshotDir, rel), 'utf8');
    // No live include directive.
    if (/```[^\n]*\bfile=agentforge4j/.test(text)) {
      throw new Error(`scratch-cut: snapshot ${rel} still has a live include directive (file=)`);
    }
    // No live vocab/javadoc inline-code directive.
    if (/`vocab:[a-z]+:/.test(text)) {
      throw new Error(`scratch-cut: snapshot ${rel} still has a live vocab: directive`);
    }
    if (/`javadoc:com\.agentforge4j/.test(text)) {
      throw new Error(`scratch-cut: snapshot ${rel} still has a live javadoc: directive`);
    }
    // Javadoc references must be pinned to the scratch version, never the moving `next`.
    if (text.includes('/javadoc/next/')) {
      throw new Error(`scratch-cut: snapshot ${rel} links Javadoc at /javadoc/next/ (should be pinned to ${version})`);
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
    console.log('[scratch-cut] mechanism proven.');
  } finally {
    // 4. Full revert to the recorded pre-state.
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

  // 5. Net-zero assertion: the simulation left no residue.
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

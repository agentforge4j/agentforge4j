// SPDX-License-Identifier: Apache-2.0
//
// Release cut — step 2: turn a staged, materialised docs tree into an immutable
// Docusaurus version snapshot.
//
// `docusaurus docs:version <v>` snapshots whatever is at the configured `docs/` path and offers no
// source-directory flag (grounded). So the cut temporarily *swaps* the materialised staged tree in for
// the editable `docs/`, runs `docs:version`, then swaps the live tree back — all under try/finally, so
// the editable `docs/` is always restored even if the version command fails. A content-hash check
// asserts `docs/` came back byte-identical.
//
// The resulting `versioned_docs/version-<v>/` contains only materialised content, satisfying the
// immutable-history guarantee (no live directive re-reads current source at a later build).

import {execFileSync} from 'node:child_process';
import {renameSync, rmSync} from 'node:fs';
import {join} from 'node:path';
import {stage} from './release-stage.mjs';
import {
  MODULE_ROOT,
  DOCS_DIR,
  DOCUSAURUS_BIN,
  STAGING_ROOT,
  STAGED_DOCS,
  VERSIONED_DOCS,
  hashTree,
  pathExists,
  validateVersion,
} from './release-paths.mjs';

const BACKUP = join(STAGING_ROOT, 'docs-live-backup');

function runDocsVersion(version) {
  console.log(`[release-cut] docusaurus docs:version ${version}`);
  execFileSync(process.execPath, [DOCUSAURUS_BIN, 'docs:version', version], {
    cwd: MODULE_ROOT,
    stdio: 'inherit',
  });
}

/**
 * Cut an immutable version snapshot from a materialised staged tree.
 *
 * @param {string} version the version to create (must not already exist)
 * @param {{stagedDocs?: string, autoStage?: boolean}} [options] `autoStage` runs staging first
 */
export function cut(version, options = {}) {
  validateVersion(version);
  if (pathExists(join(VERSIONED_DOCS, `version-${version}`))) {
    throw new Error(`release-cut: version '${version}' already exists — refusing to overwrite`);
  }

  const stagedDocs = options.stagedDocs || STAGED_DOCS;
  if (options.autoStage) {
    stage(version);
  }
  if (!pathExists(stagedDocs)) {
    throw new Error(`release-cut: no staged docs at ${stagedDocs}. Run release-stage (or pass autoStage) first.`);
  }

  const liveHashBefore = hashTree(DOCS_DIR);
  rmSync(BACKUP, {recursive: true, force: true});
  renameSync(DOCS_DIR, BACKUP); // move the editable tree aside

  try {
    renameSync(stagedDocs, DOCS_DIR); // the materialised tree stands in as docs/
    runDocsVersion(version);
  } finally {
    // Remove whatever now sits at docs/ (the materialised tree, or a partial on failure) and restore
    // the editable tree unconditionally.
    rmSync(DOCS_DIR, {recursive: true, force: true});
    renameSync(BACKUP, DOCS_DIR);
  }

  const liveHashAfter = hashTree(DOCS_DIR);
  if (liveHashAfter !== liveHashBefore) {
    throw new Error('release-cut: the editable docs/ was not restored byte-identically after the cut');
  }
  console.log(`[release-cut] created version-${version} (docs/ restored byte-identical)`);
}

// CLI entry.
if (process.argv[1]?.endsWith('release-cut.mjs')) {
  cut(process.argv[2], {autoStage: true});
}

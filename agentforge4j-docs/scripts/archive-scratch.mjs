// SPDX-License-Identifier: Apache-2.0
//
// Archive-transition simulation (design §7/§12; owner lock: nothing real exists to archive before
// two releases). Proves the whole archive mechanism end-to-end against a manufactured scratch
// version, then fully reverts so nothing throwaway is ever committed:
//
//   1. record the exact pre-state (versions.json bytes, versioned_*/archive existence, git status);
//   2. manufacture a scratch version with the Phase-5a staging + cut (materialised snapshot);
//   3. run the REAL archive transition against it — isolated one-version export, frozen artifact at
//      archive/<v>/, redirect manifest, removal from the active version list;
//   4. ASSERT the artifact is a self-contained site (root page + assets baked under the archive
//      mount), the manifest maps every page route old address -> archive address, the version left
//      versions.json, and the versioned snapshot remains in-repo (provenance);
//   5. delete every produced artifact and restore the pre-state byte-for-byte;
//   6. assert `git status` is unchanged from step 1 (net-zero: the simulation left no residue).
//
// Steps 5–6 run in `finally`, so a failed assertion still reverts. Run via `npm run docs:archive-scratch`.

import {execFileSync} from 'node:child_process';
import {readFileSync, rmSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {stage} from './release-stage.mjs';
import {cut} from './release-cut.mjs';
import {archiveTransition, ARCHIVE_ROOT} from './archive-transition.mjs';
import {
  MODULE_ROOT,
  STAGING_ROOT,
  VERSIONS_JSON,
  VERSIONED_DOCS,
  VERSIONED_SIDEBARS,
  pathExists,
  validateVersion,
} from './release-paths.mjs';

const REPO_ROOT = join(MODULE_ROOT, '..');
const SCRATCH_VERSION = validateVersion(process.argv[2] || '0.0.0-scratch');
// Synthetic "newer" placeholders (never staged/cut for real — they exist only as versions.json
// entries) so the freshly-cut scratch version is not itself the newest/immediately-prior release.
// archiveTransition() now refuses to archive a version supportWindow() classifies as supported, and
// a lone freshly-cut version is always that — exactly what two subsequent real releases would fix
// in production. Wiped out by the byte-identical versions.json restore in `finally` below.
const SCRATCH_NEWER_1 = validateVersion('9.9.9-scratch-newer-1');
const SCRATCH_NEWER_2 = validateVersion('9.9.9-scratch-newer-2');

function gitStatus() {
  return execFileSync('git', ['status', '--porcelain'], {cwd: REPO_ROOT, encoding: 'utf8'});
}

function readOrNull(path) {
  return pathExists(path) ? readFileSync(path) : null;
}

/** Assert the frozen artifact + manifest have the shape the Pages assembly consumes. */
function assertArchived(version) {
  const artifactDir = join(ARCHIVE_ROOT, version);
  const manifestPath = join(ARCHIVE_ROOT, `${version}.redirects.json`);

  // Self-contained site: a root page, and every asset/route baked under the archive mount.
  if (!pathExists(join(artifactDir, 'index.html'))) {
    throw new Error(`archive-scratch: archive/${version}/index.html missing — not a browsable artifact`);
  }
  const rootPage = readFileSync(join(artifactDir, 'index.html'), 'utf8');
  if (!rootPage.includes(`/docs/archive/${version}/`)) {
    throw new Error(`archive-scratch: the artifact root page does not reference the /docs/archive/${version}/ mount — baseUrl not applied`);
  }
  if (rootPage.includes('"/docs/next/') || rootPage.includes('"/docs/latest')) {
    throw new Error('archive-scratch: the artifact references live-site routes (/docs/next or /docs/latest) — not self-contained');
  }

  // Manifest: every entry maps the version's old active address to its archive address.
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  if (!Array.isArray(manifest) || manifest.length === 0) {
    throw new Error('archive-scratch: the redirect manifest is empty');
  }
  for (const {from, to} of manifest) {
    if (!from.startsWith(`/docs/${version}`) || !to.startsWith(`/docs/archive/${version}`)) {
      throw new Error(`archive-scratch: malformed manifest entry ${from} -> ${to}`);
    }
    if (to !== from.replace(`/docs/${version}`, `/docs/archive/${version}`)) {
      throw new Error(`archive-scratch: manifest entry does not mirror its route: ${from} -> ${to}`);
    }
  }

  // The version left the active list; the snapshot remains as provenance.
  const versions = pathExists(VERSIONS_JSON) ? JSON.parse(readFileSync(VERSIONS_JSON, 'utf8')) : [];
  if (versions.includes(version)) {
    throw new Error(`archive-scratch: ${version} is still in versions.json after the transition`);
  }
  if (!pathExists(join(VERSIONED_DOCS, `version-${version}`))) {
    throw new Error(`archive-scratch: versioned_docs/version-${version} was removed — provenance must stay in-repo (design §7)`);
  }

  console.log(`[archive-scratch] artifact verified: self-contained at archive/${version}, ${manifest.length} redirect route(s), out of the active build, provenance kept`);
}

function main() {
  if (pathExists(join(ARCHIVE_ROOT, SCRATCH_VERSION)) || pathExists(join(VERSIONED_DOCS, `version-${SCRATCH_VERSION}`))) {
    throw new Error(`archive-scratch: ${SCRATCH_VERSION} artifacts already exist — refusing to run`);
  }

  // 1. Pre-state.
  const preStatus = gitStatus();
  const preVersionsJson = readOrNull(VERSIONS_JSON);
  const preVersionedDocsExisted = pathExists(VERSIONED_DOCS);
  const preVersionedSidebarsExisted = pathExists(VERSIONED_SIDEBARS);
  const preArchiveExisted = pathExists(ARCHIVE_ROOT);
  const sidebarFile = join(VERSIONED_SIDEBARS, `version-${SCRATCH_VERSION}-sidebars.json`);

  try {
    // 2. Manufacture the scratch version (materialised, exactly like a real cut).
    stage(SCRATCH_VERSION);
    cut(SCRATCH_VERSION);
    const versionsAfterCut = JSON.parse(readFileSync(VERSIONS_JSON, 'utf8'));
    writeFileSync(
      VERSIONS_JSON,
      `${JSON.stringify([SCRATCH_NEWER_2, SCRATCH_NEWER_1, ...versionsAfterCut], null, 2)}\n`,
      'utf8',
    );

    // 3. The real transition.
    archiveTransition(SCRATCH_VERSION);

    // 4. Prove the artifact.
    assertArchived(SCRATCH_VERSION);
    console.log('[archive-scratch] mechanism proven.');
  } finally {
    // 5. Full revert to the recorded pre-state.
    rmSync(join(ARCHIVE_ROOT, SCRATCH_VERSION), {recursive: true, force: true});
    rmSync(join(ARCHIVE_ROOT, `${SCRATCH_VERSION}.redirects.json`), {force: true});
    if (!preArchiveExisted) {
      rmSync(ARCHIVE_ROOT, {recursive: true, force: true});
    }
    rmSync(join(VERSIONED_DOCS, `version-${SCRATCH_VERSION}`), {recursive: true, force: true});
    rmSync(sidebarFile, {force: true});
    if (!preVersionedDocsExisted) {
      rmSync(VERSIONED_DOCS, {recursive: true, force: true});
    }
    if (!preVersionedSidebarsExisted) {
      rmSync(VERSIONED_SIDEBARS, {recursive: true, force: true});
    }
    if (preVersionsJson === null) {
      rmSync(VERSIONS_JSON, {force: true});
    } else {
      writeFileSync(VERSIONS_JSON, preVersionsJson);
    }
    rmSync(STAGING_ROOT, {recursive: true, force: true});
  }

  // 6. Net-zero assertion.
  const postStatus = gitStatus();
  if (postStatus !== preStatus) {
    throw new Error(
      'archive-scratch: git status changed after revert — the simulation left residue:\n' +
        `--- before ---\n${preStatus}\n--- after ---\n${postStatus}`,
    );
  }
  console.log('[archive-scratch] reverted cleanly — git status unchanged.');
}

main();

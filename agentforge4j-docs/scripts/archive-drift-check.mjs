// SPDX-License-Identifier: Apache-2.0
//
// Archive drift gate (design §7/§12, D-2 follow-up). `archiveTransition()` (archive-transition.mjs)
// stays a manual, maintainer-run CLI (`npm run docs:archive <version>`) on purpose: it produces a
// committed artifact plus a `versions.json` edit that must go through a real PR, so nothing may run
// it unattended on a CI runner. What CI CAN and does do is DETECT when support-window.mjs's policy
// says a version should already be archived but `archive/` does not have it yet, and fail loudly with
// the exact command to run — turning a silent policy drift into a build failure no maintainer can
// miss, rather than leaving it to be noticed by hand.
//
// Pre-`0.1.0` `versions.json` is empty, so `supportWindow()` classifies nothing as archived and this
// is a no-op. Wired into `npm run check`, so it runs in the Docs CI gate (docs.yml, every push/PR to
// `main`) and the Pages deploy — both consume the same shared build-docs-site composite action, so
// the gate and the deploy can never drift apart on this check either.
//
// Run via `npm run docs:archive-check` (folded into `npm run lint` / `npm run check`).

import {existsSync, readdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {ARCHIVE_ROOT} from './archive-transition.mjs';
import {MODULE_ROOT, VERSIONS_JSON, pathExists} from './release-paths.mjs';
import {supportWindow} from './support-window.mjs';

const LTS_JSON = join(MODULE_ROOT, 'lts.json');

/** Read a version-list JSON file (versions.json / lts.json), or [] if absent — same as the config. */
function readVersionList(path) {
  return pathExists(path) ? JSON.parse(readFileSync(path, 'utf8')) : [];
}

/** The archived version names actually present on disk (a directory under archive/). */
function archivedOnDisk() {
  return existsSync(ARCHIVE_ROOT)
    ? readdirSync(ARCHIVE_ROOT, {withFileTypes: true})
        .filter((entry) => entry.isDirectory())
        .map((entry) => entry.name)
    : [];
}

/**
 * Compare support-window.mjs's policy classification against archive/'s actual contents. A version
 * still counts as active (still in `versions`) right up until `archiveTransition()` actually runs for
 * it — which is exactly the window this gate exists to catch: policy says "archive me" but nobody has.
 *
 * @param {string[]} versions versions.json contents (still-active versions, newest first)
 * @param {string[]} lts lts.json contents
 * @param {string[]} onDisk archived version names actually present under archive/ (order-independent)
 * @returns {string[]} versions policy classifies as archived that archive/ does not yet cover — pure,
 *          unit-tested independent of the filesystem
 */
export function archiveDrift(versions, lts, onDisk) {
  const {archived} = supportWindow(versions, lts);
  const onDiskSet = new Set(onDisk);
  return archived.filter((version) => !onDiskSet.has(version));
}

// CLI entry.
if (process.argv[1]?.endsWith('archive-drift-check.mjs')) {
  const drifted = archiveDrift(readVersionList(VERSIONS_JSON), readVersionList(LTS_JSON), archivedOnDisk());
  if (drifted.length > 0) {
    console.error('[archive-drift-check] the following version(s) have left the support window (per');
    console.error('  support-window.mjs) but have not yet been archived:');
    for (const version of drifted) {
      console.error(`    ${version} — run: npm run docs:archive ${version}`);
    }
    console.error('  Commit the resulting archive/<version>/ artifact and updated versions.json before');
    console.error('  the next deploy.');
    process.exit(1);
  }
  console.log('[archive-drift-check] no drift — every version support-window.mjs classifies as archived is already archived.');
}

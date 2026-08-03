// SPDX-License-Identifier: Apache-2.0
//
// Shared build-provenance marker for the assembled `.org` site (`agentforge4j-docs/_site`) —
// written by `build-assembled-site.mjs` after a successful build, verified by
// `release-check.mjs` before it trusts `_site` as evidence of CURRENT relevant source state.
// `existsSync(_site/index.html)` alone (the previous check) only proves *a* composed site exists,
// never that it reflects anything close to the current source — a `_site` built hours or days ago,
// against an old commit, would pass that check silently.
//
// The marker records the commit sha at build time — which, since git is a content-addressed
// store, already IS a deterministic hash over the entire relevant source tree at that commit — plus
// the exact list of relevant files (if any) that were dirty (uncommitted) at build time. A build
// taken from a dirty relevant tree can never be trusted as reflecting a durable, reviewable source
// state (the same reasoning check-freshness.mjs's own --strict dirty-check applies to the
// attestation), so `evaluateSiteProvenance` fails closed on that case exactly as it fails closed on
// a stale or missing marker.

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

export const PROVENANCE_FILENAME = '.build-provenance.json';

export function provenancePath(siteDir) {
  return join(siteDir, PROVENANCE_FILENAME);
}

/** Writes the marker after a successful build. `dirtyRelevantFilesAtBuildTime` should be the exact
 *  list `check-freshness.mjs`'s `getDirtyRelevantFiles()` returned at build time — the same
 *  canonical "relevant files" definition used everywhere else in this evidence chain. */
export function writeProvenanceMarker(siteDir, { commitSha, dirtyRelevantFilesAtBuildTime }) {
  const marker = {
    builtAt: new Date().toISOString(),
    commitSha,
    dirtyRelevantFilesAtBuildTime,
  };
  writeFileSync(provenancePath(siteDir), `${JSON.stringify(marker, null, 2)}\n`);
  return marker;
}

/** Reads and parses the marker, or returns `null` if it's missing, unreadable, or not valid JSON —
 *  never throws. A malformed marker is exactly as untrustworthy as a missing one to
 *  `evaluateSiteProvenance`, which treats `null` as "no marker found". */
export function readProvenanceMarker(siteDir) {
  const path = provenancePath(siteDir);
  if (!existsSync(path)) {
    return null;
  }
  try {
    const parsed = JSON.parse(readFileSync(path, 'utf8'));
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Pure: decides whether the assembled site can be trusted as reflecting the current relevant
 * source state. Separated from the real file-read/git-invocation so this is directly unit-testable
 * against fixture markers, without a real `_site` directory or git repository.
 */
export function evaluateSiteProvenance(marker, currentCommitSha, currentlyDirtyRelevantFiles) {
  if (!marker) {
    return {
      fresh: false,
      reason: 'no build-provenance marker found at _site/.build-provenance.json — the composed ' +
        'site may predate the current source, or was built by a version of build-assembled-site.mjs ' +
        'that did not record one. Run `npm run visual:build-site` to rebuild.',
    };
  }
  if (typeof marker.commitSha !== 'string' || marker.commitSha.length === 0) {
    return {
      fresh: false,
      reason: '_site/.build-provenance.json is missing a valid commitSha field — cannot confirm ' +
        'freshness. Run `npm run visual:build-site` to rebuild.',
    };
  }
  if (marker.commitSha !== currentCommitSha) {
    return {
      fresh: false,
      reason: `the composed site was built from commit ${marker.commitSha.slice(0, 12)}, not the ` +
        `current ${currentCommitSha.slice(0, 12)}. Run \`npm run visual:build-site\` to rebuild.`,
    };
  }
  const dirtyAtBuild = Array.isArray(marker.dirtyRelevantFilesAtBuildTime) ? marker.dirtyRelevantFilesAtBuildTime : null;
  if (dirtyAtBuild === null) {
    return {
      fresh: false,
      reason: '_site/.build-provenance.json is missing a valid dirtyRelevantFilesAtBuildTime field ' +
        '— cannot confirm the build reflected a clean, durable source state. Run ' +
        '`npm run visual:build-site` to rebuild.',
    };
  }
  if (dirtyAtBuild.length > 0) {
    return {
      fresh: false,
      reason: `the composed site was built while relevant source file(s) were uncommitted: ` +
        `${dirtyAtBuild.slice(0, 5).join(', ')}${dirtyAtBuild.length > 5 ? ` (+${dirtyAtBuild.length - 5} more)` : ''}. ` +
        'A build taken from a dirty tree is not durable, reviewable evidence — commit first, then ' +
        'run `npm run visual:build-site` again.',
    };
  }
  if (currentlyDirtyRelevantFiles.length > 0) {
    return {
      fresh: false,
      reason: `relevant source file(s) are currently uncommitted, since this build: ` +
        `${currentlyDirtyRelevantFiles.slice(0, 5).join(', ')}` +
        `${currentlyDirtyRelevantFiles.length > 5 ? ` (+${currentlyDirtyRelevantFiles.length - 5} more)` : ''}. ` +
        'Commit them, then run `npm run visual:build-site` again.',
    };
  }
  return { fresh: true };
}

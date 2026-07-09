// SPDX-License-Identifier: Apache-2.0
//
// Archive transition (design §7/§12). When a released version leaves the support window (see
// support-window.mjs), it stops being part of the active build and becomes a SELF-CONTAINED static
// artifact: a one-version Docusaurus export built with the archive-mode config (see
// docusaurus.config.ts, AF4J_ARCHIVE_VERSION), mounted at `/docs/archive/<v>/`.
//
//   1. build the isolated one-version export into the gitignored staging area;
//   2. copy it to `archive/<v>/` — a committed, frozen artifact the Pages assembly carries forward
//      on every deploy (it is never rebuilt; the snapshot it renders was materialised at cut time,
//      so nothing in it can re-read current source);
//   3. emit the redirect manifest `archive/<v>.redirects.json` mapping every page route of the
//      version's old active address (`/docs/<v>/...`) to its archive address
//      (`/docs/archive/<v>/...`) — the Pages assembly publishes these as static redirect stubs;
//   4. remove `<v>` from `versions.json` (it leaves the active build), while `versioned_docs/
//      version-<v>/` deliberately stays in-repo as provenance (design §7).
//
// Nothing here runs pre-`0.1.0` for real — `npm run docs:archive-scratch` proves the mechanism
// against a manufactured scratch version and reverts (see archive-scratch.mjs).

import {execFileSync} from 'node:child_process';
import {cpSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {
  MODULE_ROOT,
  STAGING_ROOT,
  VERSIONS_JSON,
  VERSIONED_DOCS,
  listFiles,
  pathExists,
  validateVersion,
} from './release-paths.mjs';

/** The committed archive artifacts (design §7): `archive/<v>/` + `archive/<v>.redirects.json`. */
export const ARCHIVE_ROOT = join(MODULE_ROOT, 'archive');
// The Docusaurus CLI entry, run directly via node — no shell, so arguments cannot be shell-interpreted.
const DOCUSAURUS_BIN = join(MODULE_ROOT, 'node_modules', '@docusaurus', 'core', 'bin', 'docusaurus.mjs');
const EXPORT_BUILD = join(STAGING_ROOT, 'archive-build');

/**
 * Derive the page routes of an exported site from its file listing: every `index.html` directory is
 * a route; other files (assets, sitemap, 404) are not addressable pages. Pure — unit-tested.
 *
 * @param {string[]} files repo-relative POSIX paths of the export (as produced by listFiles)
 * @returns {string[]} page routes, '' for the root, sorted
 */
export function pageRoutes(files) {
  return files
    .filter((file) => file === 'index.html' || file.endsWith('/index.html'))
    .map((file) => (file === 'index.html' ? '' : file.slice(0, -'/index.html'.length)))
    .sort();
}

/**
 * Build the redirect manifest for an archived version: every page route of the old active address
 * maps to the same route under the archive mount. Pure — unit-tested.
 *
 * @param {string} version the archived version
 * @param {string[]} routes page routes from {@link pageRoutes}
 * @returns {{from: string, to: string}[]}
 */
export function redirectManifest(version, routes) {
  return routes.map((route) => ({
    from: route === '' ? `/docs/${version}` : `/docs/${version}/${route}`,
    to: route === '' ? `/docs/archive/${version}` : `/docs/archive/${version}/${route}`,
  }));
}

/**
 * Transition one released version out of the active build into a self-contained static archive.
 *
 * @param {string} version the version to archive (must exist as a versioned snapshot)
 * @returns {{artifactDir: string, manifestPath: string, routeCount: number}}
 */
export function archiveTransition(version) {
  validateVersion(version);
  if (!pathExists(join(VERSIONED_DOCS, `version-${version}`))) {
    throw new Error(`archive-transition: no versioned snapshot for '${version}' (versioned_docs/version-${version} missing)`);
  }
  const versions = pathExists(VERSIONS_JSON) ? JSON.parse(readFileSync(VERSIONS_JSON, 'utf8')) : [];
  if (!versions.includes(version)) {
    throw new Error(`archive-transition: '${version}' is not in versions.json (already archived, or never released)`);
  }
  const artifactDir = join(ARCHIVE_ROOT, version);
  const manifestPath = join(ARCHIVE_ROOT, `${version}.redirects.json`);
  if (pathExists(artifactDir) || pathExists(manifestPath)) {
    throw new Error(`archive-transition: archive/${version} already exists — refusing to overwrite a frozen artifact`);
  }

  // 1. Isolated one-version static export (archive-mode config).
  console.log(`[archive-transition] building the self-contained export for ${version}`);
  rmSync(EXPORT_BUILD, {recursive: true, force: true});
  execFileSync(process.execPath, [DOCUSAURUS_BIN, 'build', '--out-dir', EXPORT_BUILD], {
    cwd: MODULE_ROOT,
    stdio: 'inherit',
    env: {...process.env, AF4J_ARCHIVE_VERSION: version},
  });

  try {
    // 2. Freeze the artifact.
    mkdirSync(ARCHIVE_ROOT, {recursive: true});
    cpSync(EXPORT_BUILD, artifactDir, {recursive: true});

    // 3. Redirect manifest: old active address -> archive address, one entry per page route. The
    // `search` page is excluded: the live site serves search site-globally (/docs/search), so a
    // version-scoped /docs/<v>/search never existed as an address to redirect from.
    const routes = pageRoutes(listFiles(artifactDir)).filter((route) => route !== 'search');
    if (routes.length === 0) {
      throw new Error(`archive-transition: the ${version} export contains no page routes`);
    }
    writeFileSync(manifestPath, `${JSON.stringify(redirectManifest(version, routes), null, 2)}\n`, 'utf8');

    // 4. Leave the active build: drop the version from versions.json. The versioned snapshot
    // (versioned_docs/version-<v>) deliberately stays in-repo as provenance (design §7).
    writeFileSync(
      VERSIONS_JSON,
      `${JSON.stringify(versions.filter((v) => v !== version), null, 2)}\n`,
      'utf8',
    );

    console.log(`[archive-transition] archived ${version}: ${routes.length} route(s) at archive/${version}, manifest written, removed from the active version list`);
    return {artifactDir, manifestPath, routeCount: routes.length};
  } catch (err) {
    // A half-produced archive must not survive: it would block the rerun (refuse-to-overwrite)
    // and could be mistaken for a frozen artifact. Both paths are products of THIS run only —
    // pre-existing ones were refused above. versions.json is only written as the last step, so a
    // failure before it leaves the active list untouched.
    rmSync(artifactDir, {recursive: true, force: true});
    rmSync(manifestPath, {force: true});
    throw err;
  } finally {
    rmSync(EXPORT_BUILD, {recursive: true, force: true});
  }
}

// CLI entry.
if (process.argv[1]?.endsWith('archive-transition.mjs')) {
  archiveTransition(process.argv[2]);
}

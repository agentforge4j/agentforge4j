// SPDX-License-Identifier: Apache-2.0
//
// Pages artifact assembly (design §12, Phase 5c). GitHub Pages publishes ONE artifact per deploy
// (full replace), so a single deploy must compose every independently-built surface into one tree:
//
//   _site/
//     docs/          <- the Docusaurus build (baseUrl /docs/, so build/* maps under /docs/)
//     javadoc/next/  <- the aggregate Javadoc surface (build-javadoc output)
//     javadoc/<v>/   <- a version-pinned Javadoc surface per active OR archived version (build-javadoc-versions.mjs)
//     javadoc/latest/<- the moving alias (newest stable once one exists, else mirrors next)
//     docs/archive/  <- carried-forward frozen versions (design §7; no-op until archives exist), plus
//                        static redirect stubs at each archived version's old active address
//     index.html     <- root redirect to the docs entry
//     CNAME          <- the custom domain, ONLY when DOCS_CUSTOM_DOMAIN is set (default: omitted)
//     .nojekyll      <- disable Jekyll so files/dirs starting with _ are served
//
// Because javadoc is rebuilt from source on every deploy (for both active AND archived versions —
// `main()` below and build-javadoc-versions.mjs's own CLI entry both compute the same active-plus-
// archived union via the shared `javadocBuildVersions` helper) and the docs archive is carried
// forward here, a routine docs redeploy never drops /javadoc/** or /docs/archive/** — the
// additive-composition guarantee on a full-replace host. Fails closed if a required input is missing.
//
// Run via `node scripts/assemble-site.mjs` (usually from the deploy workflow).

import {cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {supportWindow} from './support-window.mjs';
import {docsEntryPath} from './redirect-config.mjs';
import {JAVADOC_VERSIONS_OUT, javadocBuildVersions} from './build-javadoc-versions.mjs';
import {ARCHIVE_ROOT} from './archive-transition.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = resolve(here, '..');
const REPO_ROOT = resolve(MODULE_ROOT, '..');

const BUILD_DIR = join(MODULE_ROOT, 'build');
const JAVADOC_DIR = join(REPO_ROOT, 'agentforge4j-docs-javadoc', 'build-javadoc', 'next');
const SITE_DIR = join(MODULE_ROOT, '_site');

// Custom-domain opt-in. A CNAME file claims the domain for this Pages site, and one domain can
// serve only one Pages site — so the artifact carries NO CNAME unless the deploy explicitly sets
// DOCS_CUSTOM_DOMAIN (e.g. `agentforge4j.org`) once the domain/publishing composition is settled.
const CUSTOM_DOMAIN = process.env.DOCS_CUSTOM_DOMAIN || null;

/** Read a version-list JSON file (versions.json / lts.json), or [] if absent — same as the config. */
function readVersionList(path) {
  return existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : [];
}

const RELEASED_VERSIONS = readVersionList(join(MODULE_ROOT, 'versions.json'));

// The site-root redirect target. Derived from the SAME support window as the in-build redirect
// toggle and the navbar/footer targets (docsEntryPath is the single source of truth for "where do I
// point today"), so it follows the first release automatically: `/docs/next/` while no
// stable version exists, `/docs/<newest stable>/` afterwards.
const DOCS_ENTRY = `/docs/${docsEntryPath(
  supportWindow(RELEASED_VERSIONS, readVersionList(join(MODULE_ROOT, 'lts.json'))),
)}/`;

// Every version whose Javadoc surface must be present: RELEASED_VERSIONS (active) plus any archived
// version (a directory under archive/) — see javadocBuildVersions. Kept separate from
// RELEASED_VERSIONS itself, which stays active-only and must not include archived versions: it also
// drives DOCS_ENTRY/supportWindow above, and `latestSource` inside assembleSite (an archived version
// must never become the /latest alias target).
const ARCHIVED_VERSION_NAMES = existsSync(ARCHIVE_ROOT)
  ? readdirSync(ARCHIVE_ROOT, {withFileTypes: true})
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
  : [];
const JAVADOC_VERSIONS_TO_PUBLISH = javadocBuildVersions(RELEASED_VERSIONS, ARCHIVED_VERSION_NAMES);

function requireDir(path, what, hint) {
  if (!existsSync(path)) {
    console.error(`[assemble-site] missing ${what}: ${path}`);
    console.error(`  ${hint}`);
    process.exit(1);
  }
}

/** A static meta-refresh redirect page. */
function redirectHtml(to) {
  return (
    `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">` +
    `<meta http-equiv="refresh" content="0; url=${to}">` +
    `<link rel="canonical" href="${to}"><title>AgentForge4j</title></head>` +
    `<body><a href="${to}">Continue to the documentation</a></body></html>\n`
  );
}

// A manifest entry is trusted to become a filesystem path segment, so it is held to the same
// fail-closed path-safety standard as every other version/path input in these scripts
// (release-paths.mjs's `validateVersion`): rooted at /docs/, no `..` traversal segment. The only
// production writer (redirectManifest, from a validateVersion'd version + real page routes) already
// satisfies this — the check guards `archive/*.redirects.json` being a plain committed JSON file a
// future hand-edit or merge-conflict resolution could otherwise corrupt undetected.
function isSafeManifestPath(path) {
  return (
    typeof path === 'string' &&
    path.startsWith('/docs/') &&
    !path.includes('\\') &&
    !path.split('/').includes('..')
  );
}

/**
 * Publish an archived version's redirect manifest as static stub pages: every page route of the
 * version's old active address (`/docs/<v>/...`) permanently forwards to its archive mount
 * (`/docs/archive/<v>/...`). Written AFTER the docs copy so a stub can never be overwritten by it.
 *
 * @param {(code: number) => void} [exit] injectable seam for the fail-closed collision guard
 *        (tests; default `process.exit`), mirroring the `builder` seam on `buildJavadocVersions`.
 */
function writeRedirectStubs(siteDir, manifestPath, exit = process.exit) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  for (const {from, to} of manifest) {
    if (!isSafeManifestPath(from) || !isSafeManifestPath(to)) {
      console.error(`[assemble-site] refusing an unsafe redirect manifest entry: ${JSON.stringify({from, to})}`);
      console.error('  Both `from` and `to` must be rooted at /docs/ with no `..` segments.');
      exit(1);
    }
    const dir = join(siteDir, ...from.split('/').filter(Boolean));
    const stub = join(dir, 'index.html');
    // A stub must never shadow a live page. The transition removes the version from versions.json
    // in the same run that freezes the artifact, so its old routes cannot exist in the live build;
    // if one does, archive/ and versions.json are inconsistent — stop rather than publish a site
    // where a redirect silently replaced real content.
    if (existsSync(stub)) {
      console.error(`[assemble-site] redirect stub would overwrite a live page: ${from}`);
      console.error('  The archived version is still part of the live build — archive/ and versions.json disagree.');
      exit(1);
    }
    mkdirSync(dir, {recursive: true});
    writeFileSync(stub, redirectHtml(`${to}/`), 'utf8');
  }
  return manifest.length;
}

/**
 * Assemble the Pages artifact into `siteDir` from the given inputs. Pure with respect to module
 * location (every path is a parameter), so it is directly unit-testable against fixture
 * directories; `main()` below is the real CLI entry, computing the live paths.
 *
 * @param {{buildDir: string, javadocDir: string, javadocVersionsDir?: string,
 *          releasedVersions?: string[], archiveDir: string, siteDir: string,
 *          docsEntry: string, customDomain: string|null,
 *          exit?: (code: number) => void}} options `exit` is an injectable seam for the
 *        redirect-stub collision guard (tests; default `process.exit`).
 */
export function assembleSite({
  buildDir,
  javadocDir,
  javadocVersionsDir,
  releasedVersions = [],
  archiveDir,
  siteDir,
  docsEntry,
  customDomain,
  exit = process.exit,
}) {
  requireDir(buildDir, 'Docusaurus build', 'Run `npm run build` first.');
  requireDir(javadocDir, 'Javadoc surface', 'Run `npm run javadoc` first.');

  rmSync(siteDir, {recursive: true, force: true});
  mkdirSync(siteDir, {recursive: true});

  // 1. Docs at /docs/ (the Docusaurus build already prefixes every route with baseUrl /docs/).
  cpSync(buildDir, join(siteDir, 'docs'), {recursive: true});

  // 2. Javadoc at /javadoc/next/, one version-pinned surface per entry in `releasedVersions` (the
  //    frozen docs snapshots link these), and the moving /javadoc/latest/ alias: the newest stable's
  //    surface once one exists, else mirrors next. `main()` below passes the union of active AND
  //    archived versions here (via build-javadoc-versions.mjs's `javadocBuildVersions`) so an
  //    archived version's Javadoc is carried forward exactly like its docs archive is in step 3 —
  //    kept as a caller-supplied list (not re-derived from `archiveDir` in here) so this function
  //    stays independently testable against fixtures that don't care about Javadoc at all.
  cpSync(javadocDir, join(siteDir, 'javadoc', 'next'), {recursive: true});
  const archiveEntries = existsSync(archiveDir) ? readdirSync(archiveDir, {withFileTypes: true}) : [];
  for (const version of releasedVersions) {
    const src = join(javadocVersionsDir, version);
    requireDir(
      src,
      `version-pinned Javadoc surface for ${version}`,
      'Run `npm run javadoc:versions` first — the frozen docs snapshot links /javadoc/' + version + '/.',
    );
    cpSync(src, join(siteDir, 'javadoc', version), {recursive: true});
  }
  const latestSource = releasedVersions.length > 0 ? join(javadocVersionsDir, releasedVersions[0]) : javadocDir;
  cpSync(latestSource, join(siteDir, 'javadoc', 'latest'), {recursive: true});

  // 3. Carry archived versions forward so a redeploy never drops them (no-op until they exist): each
  //    frozen artifact mounts at /docs/archive/<v>/, and its redirect manifest (if present) is
  //    published as static stubs at the version's old active address (design §7 — links never die).
  if (existsSync(archiveDir)) {
    let archived = 0;
    let stubs = 0;
    for (const entry of archiveEntries) {
      if (entry.isDirectory()) {
        cpSync(join(archiveDir, entry.name), join(siteDir, 'docs', 'archive', entry.name), {recursive: true});
        archived += 1;
      } else if (entry.name.endsWith('.redirects.json')) {
        stubs += writeRedirectStubs(siteDir, join(archiveDir, entry.name), exit);
      }
    }
    console.log(`[assemble-site] carried forward ${archived} archived version(s), ${stubs} redirect stub(s)`);
  }

  // 4. Root redirect, custom domain (opt-in only), and Jekyll opt-out.
  writeFileSync(join(siteDir, 'index.html'), redirectHtml(docsEntry), 'utf8');
  if (customDomain) {
    writeFileSync(join(siteDir, 'CNAME'), `${customDomain}\n`, 'utf8');
    console.log(`[assemble-site] custom domain opted in: CNAME ${customDomain}`);
  } else {
    console.log('[assemble-site] no custom domain configured — CNAME omitted (set DOCS_CUSTOM_DOMAIN to opt in)');
  }
  writeFileSync(join(siteDir, '.nojekyll'), '', 'utf8');

  console.log(`[assemble-site] composed ${siteDir}: /docs, /javadoc/{next,latest}, root redirect -> ${docsEntry}`);
}

function main() {
  assembleSite({
    buildDir: BUILD_DIR,
    javadocDir: JAVADOC_DIR,
    javadocVersionsDir: JAVADOC_VERSIONS_OUT,
    releasedVersions: JAVADOC_VERSIONS_TO_PUBLISH,
    archiveDir: ARCHIVE_ROOT,
    siteDir: SITE_DIR,
    docsEntry: DOCS_ENTRY,
    customDomain: CUSTOM_DOMAIN,
  });
}

// CLI entry. Guarded so assembleSite() can be unit-tested against fixture directories without
// requiring a real Docusaurus build / Javadoc surface.
if (process.argv[1]?.endsWith('assemble-site.mjs')) {
  main();
}

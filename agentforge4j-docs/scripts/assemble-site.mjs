// SPDX-License-Identifier: Apache-2.0
//
// Pages artifact assembly (design §12, Phase 5c). GitHub Pages publishes ONE artifact per deploy
// (full replace), so a single deploy must compose every independently-built surface into one tree:
//
//   _site/
//     docs/          <- the Docusaurus build (baseUrl /docs/, so build/* maps under /docs/)
//     javadoc/next/  <- the aggregate Javadoc surface (build-javadoc output)
//     javadoc/<v>/   <- a version-pinned Javadoc surface per released version (build-javadoc-versions.mjs)
//     javadoc/latest/<- the moving alias (newest stable once one exists, else mirrors next)
//     docs/archive/  <- carried-forward frozen versions (design §7; no-op until archives exist), plus
//                        static redirect stubs at each archived version's old active address
//     index.html     <- root redirect to the docs entry
//     CNAME          <- the custom domain, ONLY when DOCS_CUSTOM_DOMAIN is set (default: omitted)
//     .nojekyll      <- disable Jekyll so files/dirs starting with _ are served
//
// Because javadoc is rebuilt from source on every deploy and the archive is carried forward here, a
// routine docs redeploy never drops /javadoc/** or /docs/archive/** — the additive-composition
// guarantee on a full-replace host. Fails closed if a required input is missing.
//
// Run via `node scripts/assemble-site.mjs` (usually from the deploy workflow).

import {cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {supportWindow} from './support-window.mjs';
import {docsEntryPath} from './redirect-config.mjs';
import {JAVADOC_VERSIONS_OUT} from './build-javadoc-versions.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = resolve(here, '..');
const REPO_ROOT = resolve(MODULE_ROOT, '..');

const BUILD_DIR = join(MODULE_ROOT, 'build');
const JAVADOC_DIR = join(REPO_ROOT, 'agentforge4j-docs-javadoc', 'build-javadoc', 'next');
// Optional committed/carried-forward frozen versions (design §7). Absent pre-0.1.0.
const ARCHIVE_DIR = join(MODULE_ROOT, 'archive');
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

/**
 * Publish an archived version's redirect manifest as static stub pages: every page route of the
 * version's old active address (`/docs/<v>/...`) permanently forwards to its archive mount
 * (`/docs/archive/<v>/...`). Written AFTER the docs copy so a stub can never be overwritten by it.
 */
function writeRedirectStubs(siteDir, manifestPath) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  for (const {from, to} of manifest) {
    const dir = join(siteDir, ...from.split('/').filter(Boolean));
    mkdirSync(dir, {recursive: true});
    writeFileSync(join(dir, 'index.html'), redirectHtml(`${to}/`), 'utf8');
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
 *          docsEntry: string, customDomain: string|null}} options
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
}) {
  requireDir(buildDir, 'Docusaurus build', 'Run `npm run build` first.');
  requireDir(javadocDir, 'Javadoc surface', 'Run `npm run javadoc` first.');

  rmSync(siteDir, {recursive: true, force: true});
  mkdirSync(siteDir, {recursive: true});

  // 1. Docs at /docs/ (the Docusaurus build already prefixes every route with baseUrl /docs/).
  cpSync(buildDir, join(siteDir, 'docs'), {recursive: true});

  // 2. Javadoc at /javadoc/next/, one version-pinned surface per released version (built from each
  //    release tag by build-javadoc-versions.mjs — the frozen docs snapshots link these), and the
  //    moving /javadoc/latest/ alias: the newest stable's surface once one exists, else mirrors next.
  cpSync(javadocDir, join(siteDir, 'javadoc', 'next'), {recursive: true});
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
    for (const entry of readdirSync(archiveDir, {withFileTypes: true})) {
      if (entry.isDirectory()) {
        cpSync(join(archiveDir, entry.name), join(siteDir, 'docs', 'archive', entry.name), {recursive: true});
        archived += 1;
      } else if (entry.name.endsWith('.redirects.json')) {
        stubs += writeRedirectStubs(siteDir, join(archiveDir, entry.name));
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
    releasedVersions: RELEASED_VERSIONS,
    archiveDir: ARCHIVE_DIR,
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

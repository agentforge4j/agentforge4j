// SPDX-License-Identifier: Apache-2.0
//
// Pages artifact assembly (design §12, Phase 5c). GitHub Pages publishes ONE artifact per deploy
// (full replace), so a single deploy must compose every independently-built surface into one tree:
//
//   _site/
//     docs/          <- the Docusaurus build (baseUrl /docs/, so build/* maps under /docs/)
//     javadoc/next/  <- the aggregate Javadoc surface (build-javadoc output)
//     javadoc/latest/<- the moving alias (pre-release: mirrors next)
//     docs/archive/  <- carried-forward frozen versions (design §7; no-op until archives exist)
//     index.html     <- root redirect to the docs entry
//     CNAME          <- the custom domain
//     .nojekyll      <- disable Jekyll so files/dirs starting with _ are served
//
// Because javadoc is rebuilt from source on every deploy and the archive is carried forward here, a
// routine docs redeploy never drops /javadoc/** or /docs/archive/** — the additive-composition
// guarantee on a full-replace host. Fails closed if a required input is missing.
//
// Run via `node scripts/assemble-site.mjs` (usually from the deploy workflow).

import {cpSync, existsSync, mkdirSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = resolve(here, '..');
const REPO_ROOT = resolve(MODULE_ROOT, '..');

const BUILD_DIR = join(MODULE_ROOT, 'build');
const JAVADOC_DIR = join(REPO_ROOT, 'agentforge4j-docs-javadoc', 'build-javadoc', 'next');
// Optional committed/carried-forward frozen versions (design §7). Absent pre-0.1.0.
const ARCHIVE_DIR = join(MODULE_ROOT, 'archive');
const SITE_DIR = join(MODULE_ROOT, '_site');

const CUSTOM_DOMAIN = 'agentforge4j.org';
const DOCS_ENTRY = '/docs/next/'; // pre-release entry; the redirect toggle moves this at first release.

function requireDir(path, what, hint) {
  if (!existsSync(path)) {
    console.error(`[assemble-site] missing ${what}: ${path}`);
    console.error(`  ${hint}`);
    process.exit(1);
  }
}

function main() {
  requireDir(BUILD_DIR, 'Docusaurus build', 'Run `npm run build` first.');
  requireDir(JAVADOC_DIR, 'Javadoc surface', 'Run `npm run javadoc` first.');

  rmSync(SITE_DIR, {recursive: true, force: true});
  mkdirSync(SITE_DIR, {recursive: true});

  // 1. Docs at /docs/ (the Docusaurus build already prefixes every route with baseUrl /docs/).
  cpSync(BUILD_DIR, join(SITE_DIR, 'docs'), {recursive: true});

  // 2. Javadoc at /javadoc/next/ and the moving /javadoc/latest/ (pre-release: mirror next).
  cpSync(JAVADOC_DIR, join(SITE_DIR, 'javadoc', 'next'), {recursive: true});
  cpSync(JAVADOC_DIR, join(SITE_DIR, 'javadoc', 'latest'), {recursive: true});

  // 3. Carry archived versions forward so a redeploy never drops them (no-op until they exist).
  if (existsSync(ARCHIVE_DIR)) {
    cpSync(ARCHIVE_DIR, join(SITE_DIR, 'docs', 'archive'), {recursive: true});
    console.log('[assemble-site] carried forward archived versions');
  }

  // 4. Root redirect, custom domain, and Jekyll opt-out.
  writeFileSync(
    join(SITE_DIR, 'index.html'),
    `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">` +
      `<meta http-equiv="refresh" content="0; url=${DOCS_ENTRY}">` +
      `<link rel="canonical" href="${DOCS_ENTRY}"><title>AgentForge4j</title></head>` +
      `<body><a href="${DOCS_ENTRY}">Continue to the documentation</a></body></html>\n`,
    'utf8',
  );
  writeFileSync(join(SITE_DIR, 'CNAME'), `${CUSTOM_DOMAIN}\n`, 'utf8');
  writeFileSync(join(SITE_DIR, '.nojekyll'), '', 'utf8');

  console.log(`[assemble-site] composed ${SITE_DIR}: /docs, /javadoc/{next,latest}, root redirect -> ${DOCS_ENTRY}`);
}

main();

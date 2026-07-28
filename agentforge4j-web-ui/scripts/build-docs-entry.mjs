// SPDX-License-Identifier: Apache-2.0
//
// Resolves, at build time, the one URL the marketing site's "Docs" links should point at, and
// writes it to src/generated/docs-entry.json for nav.ts to import.
//
// Why this exists. The site's Docs links used to target `/docs/`, which is not a page: it is a
// client-redirect stub @docusaurus/plugin-client-redirects generates, whose whole content is a
// meta refresh to the current version. Every visitor and every crawler therefore reached the
// documentation through an extra hop that only resolves once HTML (or JavaScript) has been parsed —
// and it was the ONLY route in, since nothing linked the real `/docs/<version>/` tree directly. A
// static host cannot answer that with a real 3xx (see below), so the fix is to stop routing through
// it: link the destination directly, and leave the stub in place purely for inbound links that
// already exist.
//
// Why not just hardcode `/docs/0.1.0/`. Because it would be silently wrong the day 0.2.0 ships —
// the links would keep pointing at an older version's tree, which stays live and looks perfectly
// healthy, so nothing would fail. The version lifecycle already has exactly one authority for
// "where do the current docs live": agentforge4j-docs' `supportWindow` + `docsEntryPath`, the same
// pure pair docusaurus.config.ts drives its own `/` and `/latest` redirects and its navbar/footer
// targets from. This script imports THOSE functions and reads THEIR inputs (versions.json,
// lts.json), so the site cannot disagree with the docs build about which version is current, and a
// release cut moves both together with no code change.
//
// Why not a real HTTP redirect. The site is published to GitHub Pages (see .github/workflows/
// deploy.yml, actions/deploy-pages), which serves static files and offers no redirect
// configuration of any kind — no rewrite rules, no edge config, no _redirects file. A 301 from
// `/docs/` is not something this repository can implement on this host; making the stub honest and
// routing around it is.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
// Cross-module imports of the docs module's own lifecycle logic, deliberately, rather than a second
// copy of the rules here. Both are pure and I/O-free (they take parsed arrays and return a value),
// with no dependencies of their own, so importing them costs nothing and buys the one property that
// matters: there is a single definition of "the current docs version", not two that can drift.
import { supportWindow } from '../../agentforge4j-docs/scripts/support-window.mjs';
import { docsEntryPath } from '../../agentforge4j-docs/scripts/redirect-config.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const DOCS_MODULE_ROOT = join(MODULE_ROOT, '..', 'agentforge4j-docs');
const OUT_PATH = join(MODULE_ROOT, 'src', 'generated', 'docs-entry.json');

/** The docs module's own version lists, or `[]` when absent — the same tolerant read
 * docusaurus.config.ts does for the same two files, so a pre-first-release checkout (no
 * versions.json) resolves to `next` here exactly as it does there. */
function readVersionList(path) {
  return existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : [];
}

/**
 * The site-root-relative, trailing-slash URL of the current documentation entry point — e.g.
 * `/docs/0.1.0/` once 0.1.0 is the newest supported stable, `/docs/next/` before any release.
 *
 * Trailing slash for the same reason every other internal link on this site carries one: each
 * generated docs page is a directory, which GitHub Pages serves without a redirect only at its
 * slash address.
 *
 * @param {{versions?: string[], lts?: string[]}} [lists]
 * @returns {string}
 */
export function resolveDocsEntryUrl({ versions, lts } = {}) {
  const released = versions ?? readVersionList(join(DOCS_MODULE_ROOT, 'versions.json'));
  const longTerm = lts ?? readVersionList(join(DOCS_MODULE_ROOT, 'lts.json'));
  return `/docs/${docsEntryPath(supportWindow(released, longTerm))}/`;
}

export function buildDocsEntry({ outPath = OUT_PATH } = {}) {
  const url = resolveDocsEntryUrl();
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, `${JSON.stringify({ url }, null, 2)}\n`, 'utf8');
  return url;
}

if (process.argv[1]?.endsWith('build-docs-entry.mjs')) {
  console.log(`build-docs-entry: current documentation entry point is ${buildDocsEntry()}`);
}

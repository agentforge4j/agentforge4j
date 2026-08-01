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
 * A path segment safe to splice into a site-wide URL: `next`, or a version string of the shape
 * `versions.json` actually holds.
 *
 * `versions.json` is a committed JSON file, which is exactly the argument assemble-site.mjs makes
 * for validating its own committed manifests (`isSafeManifestPath`) — the production writer already
 * satisfies the rule, and the check guards against a hand-edit or a merge-conflict resolution
 * corrupting the file undetected. Without it a stray `../` or a non-string entry becomes the href
 * on every page of the site and nothing fails: the docs build would reject the same file, but
 * `web-ui.yml` runs independently of it and would go green.
 */
function validateEntrySegment(segment) {
  if (typeof segment !== 'string' || !/^(?:next|\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)$/.test(segment)) {
    throw new Error(
      `build-docs-entry: refusing to build a docs entry URL from ${JSON.stringify(segment)} — expected ` +
        "'next' or a version of the shape agentforge4j-docs/versions.json holds. Check that file.",
    );
  }
  return segment;
}

/**
 * The site-root-relative, trailing-slash URL of the current documentation entry point — e.g.
 * `/docs/0.1.0/` once 0.1.0 is the newest supported stable, `/docs/next/` before any release.
 *
 * Trailing slash for the same reason every other internal link on this site carries one: each
 * generated docs page is a directory, which GitHub Pages serves without a redirect only at its
 * slash address.
 *
 * KNOWN LIMITATION, pre-first-release only. With an empty `versions.json` this resolves to
 * `/docs/next/`, which the site deliberately keeps out of its own index — `/docs/next/**` is
 * `noindex,follow` (agentforge4j-docs/scripts/verify-noindex.mjs) and excluded from the sitemap
 * (docusaurus.config.ts's `sitemap.ignorePatterns`). Linking it as the site's only Docs entry is
 * therefore in tension with that policy. It is accepted rather than worked around: before a first
 * release there is no indexable documentation to point at, `next` is genuinely the current docs,
 * and the alternative — linking the `/docs/` stub in that state only — reintroduces the extra hop
 * this script exists to remove. The tension disappears at the first release cut and does not exist
 * at this repository's current state (`versions.json` is non-empty).
 *
 * @param {{versions?: string[], lts?: string[]}} [lists]
 * @returns {string}
 */
export function resolveDocsEntryUrl({ versions, lts } = {}) {
  const released = versions ?? readVersionList(join(DOCS_MODULE_ROOT, 'versions.json'));
  const longTerm = lts ?? readVersionList(join(DOCS_MODULE_ROOT, 'lts.json'));
  return `/docs/${validateEntrySegment(docsEntryPath(supportWindow(released, longTerm)))}/`;
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

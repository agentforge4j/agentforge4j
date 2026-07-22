// SPDX-License-Identifier: Apache-2.0
//
// Post-build production-artifact check (same "check the real build output directly" philosophy as
// this module's own verify-noindex.mjs, which it deliberately mirrors) for this pass's two docs-side
// fixes: `trailingSlash: true` (docusaurus.config.ts) and the sitemap plugin's `lastmod: 'date'`
// option. Both are framework-native Docusaurus config, not custom code of this repo's own — this
// check exists to prove they actually took effect against a real build, and to catch a future config
// change (or a Docusaurus upgrade that alters the default) silently regressing either one.
//
// Driven entirely by sitemap.xml (not a walk of every generated HTML file): every URL the sitemap
// actually advertises to search engines must resolve to a real generated page with exactly one
// canonical tag, equal to that sitemap URL exactly (which also proves the trailing-slash form,
// since every sitemap URL itself is already checked for one). A page the sitemap never mentions at
// all (the search results page, and any other page deliberately excluded from indexing) simply
// never gets visited by this loop — no assertion is made about it either way. This closes the
// previous gap where a page missing its canonical entirely was silently skipped rather than failing
// the build: only a page's *absence from the sitemap* is allowed to opt it out now, not a missing
// tag on a page the sitemap still advertises.
//
// Wired to run right after `docusaurus build` in package.json's build script, alongside
// verify-noindex.mjs.

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');

const SITE_ORIGIN = 'https://agentforge4j.org';
const DOCS_BASE_PATH = '/docs/';

// Docusaurus's head-tag renderer does not necessarily quote attribute values with no whitespace and
// stamps a `data-rh` marker on every tag it dedupes (see verify-noindex.mjs's own note) — matched
// the same quote-agnostic way here. Collects every `<link rel="canonical">` tag individually (not
// just the first) so a page carrying more than one is caught as a duplicate, not silently resolved
// to whichever one a single greedy match happened to find first.
function canonicalHrefs(html) {
  const tags = html.match(/<link\b[^>]*\brel=["']?canonical["']?[^>]*>/gi) ?? [];
  return tags
    .map((tag) => /\bhref=["']?([^"'\s>]+)/i.exec(tag)?.[1])
    .filter((href) => href !== undefined);
}

/** Maps a sitemap URL back to the generated HTML file it must have produced — `sitemap.xml` records
 * absolute, baseUrl-inclusive URLs (`https://agentforge4j.org/docs/0.1.0/...`), while `buildDir`
 * itself is already rooted at the docs baseUrl (`0.1.0/.../index.html`) — the exact convention this
 * module's own fixture tests already write against. Returns `null` for a URL outside the expected
 * site/docs prefix, a defect in its own right the caller reports clearly rather than mis-resolving. */
function sitemapUrlToPagePath(buildDir, url) {
  const prefix = `${SITE_ORIGIN}${DOCS_BASE_PATH}`;
  if (!url.startsWith(prefix)) {
    return null;
  }
  return join(buildDir, url.slice(prefix.length), 'index.html');
}

/**
 * @param {{buildDir?: string, archiveVersion?: string|null}} [options]
 */
export function verifyCanonicalTrailingSlash({
  buildDir = join(MODULE_ROOT, 'build'),
  archiveVersion = process.env.AF4J_ARCHIVE_VERSION || null,
} = {}) {
  if (!existsSync(buildDir)) {
    throw new Error(`verify-canonical: ${buildDir} does not exist — run "docusaurus build" first`);
  }
  if (archiveVersion) {
    console.log('[verify-canonical] archive-mode build — trailing-slash/lastmod checks not applicable, skipped');
    return;
  }

  const sitemapPath = join(buildDir, 'sitemap.xml');
  if (!existsSync(sitemapPath)) {
    throw new Error(`verify-canonical: expected generated file does not exist: ${sitemapPath}`);
  }
  const sitemap = readFileSync(sitemapPath, 'utf8');
  const entries = [...sitemap.matchAll(/<url>\s*<loc>([^<]+)<\/loc>(?:\s*<lastmod>([^<]+)<\/lastmod>)?\s*<\/url>/g)];
  if (entries.length === 0) {
    throw new Error(`verify-canonical: ${sitemapPath} parsed to zero <url> entries`);
  }

  for (const [, url, lastmod] of entries) {
    if (!url.endsWith('/')) {
      throw new Error(`verify-canonical: sitemap URL "${url}" does not end in '/' (trailingSlash: true regression?)`);
    }
    if (!lastmod || !/^\d{4}-\d{2}-\d{2}$/.test(lastmod)) {
      throw new Error(
        `verify-canonical: sitemap URL "${url}" has no valid <lastmod> (YYYY-MM-DD) — ` +
          "the sitemap plugin's lastmod: 'date' option regressed, or this page has no git history",
      );
    }

    const pagePath = sitemapUrlToPagePath(buildDir, url);
    if (pagePath === null) {
      throw new Error(`verify-canonical: sitemap URL "${url}" is not rooted at ${SITE_ORIGIN}${DOCS_BASE_PATH}`);
    }
    if (!existsSync(pagePath)) {
      throw new Error(`verify-canonical: sitemap URL "${url}" has no generated page at ${pagePath}`);
    }
    const html = readFileSync(pagePath, 'utf8');
    const canonicals = canonicalHrefs(html);
    if (canonicals.length === 0) {
      throw new Error(
        `verify-canonical: ${pagePath} (sitemap URL "${url}") has no canonical tag — every page the sitemap ` +
          'advertises must declare one',
      );
    }
    if (canonicals.length > 1) {
      throw new Error(`verify-canonical: ${pagePath} has ${canonicals.length} canonical tags — expected exactly one`);
    }
    const [canonical] = canonicals;
    if (canonical !== url) {
      if (`${canonical}/` === url) {
        throw new Error(
          `verify-canonical: ${pagePath} — canonical "${canonical}" does not end in '/' (must match its sitemap ` +
            `URL "${url}" exactly)`,
        );
      }
      throw new Error(
        `verify-canonical: ${pagePath} — canonical "${canonical}" does not match its own sitemap URL "${url}" exactly`,
      );
    }
  }

  console.log(
    `[verify-canonical] verified ${entries.length} sitemap URL(s): exactly one canonical, matching its own sitemap ` +
      'URL exactly (trailing-slash form), each with a valid <lastmod>',
  );
}

function main() {
  verifyCanonicalTrailingSlash();
}

if (process.argv[1]?.endsWith('verify-canonical.mjs')) {
  main();
}

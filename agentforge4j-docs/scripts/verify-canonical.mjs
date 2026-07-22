// SPDX-License-Identifier: Apache-2.0
//
// Post-build production-artifact check (same "check the real build output directly" philosophy as
// this module's own verify-noindex.mjs, which it deliberately mirrors) for this pass's two docs-side
// fixes: `trailingSlash: true` (docusaurus.config.ts) and the sitemap plugin's `lastmod: 'date'`
// option. Both are framework-native Docusaurus config, not custom code of this repo's own — this
// check exists to prove they actually took effect against a real build, and to catch a future config
// change (or a Docusaurus upgrade that alters the default) silently regressing either one.
//
// Wired to run right after `docusaurus build` in package.json's build script, alongside
// verify-noindex.mjs.

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');

function findHtmlFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...findHtmlFiles(full));
    } else if (entry.name.endsWith('.html')) {
      results.push(full);
    }
  }
  return results;
}

// Docusaurus's head-tag renderer does not necessarily quote attribute values with no whitespace and
// stamps a `data-rh` marker on every tag it dedupes (see verify-noindex.mjs's own note) — matched
// the same quote-agnostic way here.
function canonicalHref(html) {
  const match = /<link\b[^>]*\brel=["']?canonical["']?[^>]*\bhref=["']?([^"'\s>]+)/i.exec(html);
  return match ? match[1] : null;
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

  const files = findHtmlFiles(buildDir);
  if (files.length === 0) {
    throw new Error(`verify-canonical: no generated HTML pages found under ${buildDir}`);
  }
  let checked = 0;
  for (const file of files) {
    const html = readFileSync(file, 'utf8');
    const canonical = canonicalHref(html);
    if (!canonical) {
      // Not every generated page necessarily sets its own canonical (e.g. the search results
      // page) — absence is not itself a defect this check exists to catch; a *wrong* (non-slash)
      // one is.
      continue;
    }
    if (!canonical.endsWith('/')) {
      throw new Error(
        `verify-canonical: ${file} — canonical "${canonical}" does not end in '/' ` +
          '(trailingSlash: true in docusaurus.config.ts should make every generated canonical match ' +
          'the trailing-slash address GitHub Pages actually serves with no redirect)',
      );
    }
    checked += 1;
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
  }

  console.log(
    `[verify-canonical] ${checked} page(s) with a trailing-slash-correct canonical; ` +
      `${entries.length} sitemap URL(s), all trailing-slash and carrying a valid <lastmod>`,
  );
}

function main() {
  verifyCanonicalTrailingSlash();
}

if (process.argv[1]?.endsWith('verify-canonical.mjs')) {
  main();
}

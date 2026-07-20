// SPDX-License-Identifier: Apache-2.0
//
// Post-build production-artifact check. The noindex mechanism (src/theme/DocItem/Metadata and
// src/theme/SearchPage, two thin "wrap" swizzles) only executes during a real Docusaurus SSG
// render, so it cannot be meaningfully fixture-tested the way assemble-site.mjs's pure functions
// are — this checks the real `docusaurus build` output directly, exactly like assemble-site.mjs's
// own verifyComposedArtifact checks its real composed output, and is wired to run right after
// `docusaurus build` in package.json's `build`/`check` scripts.
//
// Inventory-driven, not hardcoded to today's page/version names: every generated HTML page under
// build/next/** (discovered by recursively walking the real output, whatever pages happen to
// exist) must carry <meta name="robots" content="noindex,follow">, and so must build/search/. The
// set of "active released version" directories to check for the opposite (must NOT be noindex) is
// derived from versions.json — the same single source of truth docusaurus.config.ts itself reads
// to decide which versions get their own path — not a literal "0.1.0" written here. A future
// release, archive, or page rename changes versions.json / the real page tree, and this check
// follows along with no edit required.

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');

/** The same file docusaurus.config.ts itself reads to decide which versions get their own
 * `/<version>/` path — the one authoritative list of "active released versions." */
function readActiveVersions(moduleRoot) {
  const versionsPath = join(moduleRoot, 'versions.json');
  if (!existsSync(versionsPath)) {
    return [];
  }
  return JSON.parse(readFileSync(versionsPath, 'utf8'));
}

/** Recursively collects every generated `*.html` file under `dir`, in no particular order. */
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

// Docusaurus's own head-tag renderer (react-helmet-based) does not necessarily quote attribute
// values with no whitespace, and stamps every tag it dedupes with a `data-rh` marker attribute —
// a real generated tag looks like `<meta data-rh=true name=robots content=noindex,follow />`, not
// the literal quoted JSX text `<meta name="robots" content="noindex,follow" />` this repo's
// swizzle source writes. Match on the presence of a `<meta ...>` tag naming `robots` whose
// `content` value is `noindex,follow` (or contains `noindex` at all, for the negative check),
// independent of quoting, attribute order, or extra attributes.
function findRobotsMetaTags(html) {
  return html.match(/<meta\b[^>]*\bname=["']?robots["']?[^>]*>/gi) ?? [];
}

function hasNoindexFollow(html) {
  return findRobotsMetaTags(html).some((tag) => /\bcontent=["']?noindex,\s*follow["']?/i.test(tag));
}

function hasAnyNoindex(html) {
  return findRobotsMetaTags(html).some((tag) => /\bcontent=["']?noindex/i.test(tag));
}

/** Every `*.html` page recursively found under `dir` must satisfy `predicate`; throws a specific,
 * per-file error on the first violation. Also throws if `dir` is missing or contains no HTML
 * pages at all — a directory that fails silently to produce any page would otherwise make this
 * check vacuously pass. */
function assertEveryPage(dir, predicate, failureVerb) {
  if (!existsSync(dir)) {
    throw new Error(`verify-noindex: expected directory does not exist: ${dir}`);
  }
  const files = findHtmlFiles(dir);
  if (files.length === 0) {
    throw new Error(`verify-noindex: no generated HTML pages found under ${dir}`);
  }
  for (const file of files) {
    const html = readFileSync(file, 'utf8');
    if (!predicate(html)) {
      throw new Error(`verify-noindex: expected ${file} to ${failureVerb}, but it did not`);
    }
  }
}

/**
 * @param {{buildDir?: string, archiveVersion?: string|null, moduleRoot?: string, activeVersions?: string[]}} [options]
 */
export function verifyNoindex({
  buildDir = join(MODULE_ROOT, 'build'),
  archiveVersion = process.env.AF4J_ARCHIVE_VERSION || null,
  moduleRoot = MODULE_ROOT,
  activeVersions,
} = {}) {
  const versions = activeVersions ?? readActiveVersions(moduleRoot);
  if (!existsSync(buildDir)) {
    throw new Error(`verify-noindex: ${buildDir} does not exist — run "docusaurus build" first`);
  }

  // An archive-mode build (AF4J_ARCHIVE_VERSION set) contains only the one frozen archived
  // version at the artifact root — there is no /next or any active-version directory in that
  // output at all, so the assertions below do not apply; archive artifacts are verified by their
  // own separate process.
  if (archiveVersion) {
    console.log(`[verify-noindex] archive-mode build (${archiveVersion}) — noindex checks not applicable, skipped`);
    return;
  }

  // Every page under /next/**, whatever pages happen to exist, must be noindex,follow.
  assertEveryPage(join(buildDir, 'next'), hasNoindexFollow, 'contain a robots meta tag with content="noindex,follow"');

  // The local-search results page must be noindex,follow.
  assertEveryPage(join(buildDir, 'search'), hasNoindexFollow, 'contain a robots meta tag with content="noindex,follow"');

  // Every active released version (from versions.json) must never be noindex, on every page it
  // actually has today — not a hardcoded "0.1.0" or a hardcoded nested page name. A version listed
  // in versions.json with no matching build output directory is a build integrity failure, not a
  // silent no-op.
  for (const version of versions) {
    assertEveryPage(join(buildDir, version), (html) => !hasAnyNoindex(html), 'NOT be noindex');
  }

  // /next/** and /search must remain absent from this module's own generated sitemap.xml
  // (docusaurus.config.ts's sitemap.ignorePatterns) — the sitemap plugin records baseUrl-inclusive
  // URLs, so these are logical `/docs/...` paths regardless of the on-disk directory names above.
  const sitemapPath = join(buildDir, 'sitemap.xml');
  if (!existsSync(sitemapPath)) {
    throw new Error(`verify-noindex: expected generated file does not exist: ${sitemapPath}`);
  }
  const sitemap = readFileSync(sitemapPath, 'utf8');
  if (sitemap.includes('/docs/next/') || sitemap.includes('/docs/search')) {
    throw new Error(
      'verify-noindex: /docs/next/** or /docs/search unexpectedly present in the generated sitemap.xml',
    );
  }

  console.log('[verify-noindex] all noindex/indexable assertions passed');
}

function main() {
  verifyNoindex();
}

if (process.argv[1]?.endsWith('verify-noindex.mjs')) {
  main();
}

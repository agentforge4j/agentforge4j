// SPDX-License-Identifier: Apache-2.0
//
// Post-build production-artifact check. The noindex mechanism (src/theme/DocItem/Metadata and
// src/theme/SearchPage, two thin "wrap" swizzles) only executes during a real Docusaurus SSG
// render, so it cannot be meaningfully fixture-tested the way assemble-site.mjs's pure functions
// are — this checks the real `docusaurus build` output directly, exactly like assemble-site.mjs's
// own verifyComposedArtifact checks its real composed output, and is wired to run right after
// `docusaurus build` in package.json's `build`/`check` scripts.
//
// Every /next/** page (the unreleased, constantly-changing docs version) and the local-search
// results page must carry <meta name="robots" content="noindex,follow">; every real released
// version (e.g. /0.1.0/**) must not — and neither /next/** nor /search may appear in this
// module's own generated sitemap.xml (docusaurus.config.ts's sitemap.ignorePatterns).

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');

// Docusaurus's own head-tag renderer (react-helmet-based) does not necessarily quote attribute
// values with no whitespace, and stamps every tag it dedupes with a `data-rh` marker attribute —
// a real generated tag looks like `<meta data-rh=true name=robots content=noindex,follow />`, not
// the literal quoted JSX text `<meta name="robots" content="noindex,follow" />` this repo's
// swizzle source writes. Match on the presence of a `<meta ...>` tag naming `robots` whose
// `content` value is `noindex,follow` (or `nofollow`, for assertNotNoindex's purposes),
// independent of quoting, attribute order, or extra attributes.
function findRobotsMetaTags(html) {
  return html.match(/<meta\b[^>]*\bname=["']?robots["']?[^>]*>/gi) ?? [];
}

function readHtml(buildDir, relativePath) {
  const path = join(buildDir, ...relativePath.split('/'));
  if (!existsSync(path)) {
    throw new Error(`verify-noindex: expected generated file does not exist: ${path}`);
  }
  return readFileSync(path, 'utf8');
}

function assertNoindexFollow(buildDir, relativePath) {
  const html = readHtml(buildDir, relativePath);
  const hasNoindexFollow = findRobotsMetaTags(html).some((tag) =>
    /\bcontent=["']?noindex,\s*follow["']?/i.test(tag),
  );
  if (!hasNoindexFollow) {
    throw new Error(
      `verify-noindex: expected ${relativePath} to contain a robots meta tag with content="noindex,follow", but it did not`,
    );
  }
}

function assertNotNoindex(buildDir, relativePath) {
  const html = readHtml(buildDir, relativePath);
  const hasNoindex = findRobotsMetaTags(html).some((tag) => /\bcontent=["']?noindex/i.test(tag));
  if (hasNoindex) {
    throw new Error(`verify-noindex: expected ${relativePath} to NOT be noindex, but it is`);
  }
}

/**
 * @param {{buildDir?: string, archiveVersion?: string|null}} [options]
 */
export function verifyNoindex({
  buildDir = join(MODULE_ROOT, 'build'),
  archiveVersion = process.env.AF4J_ARCHIVE_VERSION || null,
} = {}) {
  if (!existsSync(buildDir)) {
    throw new Error(`verify-noindex: ${buildDir} does not exist — run "docusaurus build" first`);
  }

  // An archive-mode build (AF4J_ARCHIVE_VERSION set) contains only the one frozen archived
  // version at the artifact root — there is no /next or /0.1.0 in that output at all, so the
  // assertions below do not apply; archive artifacts are verified by their own separate process.
  if (archiveVersion) {
    console.log(`[verify-noindex] archive-mode build (${archiveVersion}) — noindex checks not applicable, skipped`);
    return;
  }

  // Every /next/** page must be noindex,follow — the version landing page and at least one
  // real nested page.
  assertNoindexFollow(buildDir, 'next/index.html');
  assertNoindexFollow(buildDir, 'next/concepts/workflows/index.html');

  // The local-search results page must be noindex,follow.
  assertNoindexFollow(buildDir, 'search/index.html');

  // Stable released docs (0.1.0) must never be noindex — the version landing page and the same
  // real nested page (mirrored 1:1 in versioned_docs/version-0.1.0/), so this is a genuine
  // apples-to-apples comparison against the /next/** assertions above.
  assertNotNoindex(buildDir, '0.1.0/index.html');
  assertNotNoindex(buildDir, '0.1.0/concepts/workflows/index.html');

  // /next/** and /search must remain absent from this module's own generated sitemap.xml
  // (docusaurus.config.ts's sitemap.ignorePatterns) — the sitemap plugin records baseUrl-inclusive
  // URLs, so these are logical `/docs/...` paths regardless of the on-disk directory names above.
  const sitemap = readHtml(buildDir, 'sitemap.xml');
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

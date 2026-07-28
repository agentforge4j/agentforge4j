// SPDX-License-Identifier: Apache-2.0
//
// Regression assertion owned by the docs-lastmod feature: a docs page's <lastmod>,
// once present in `agentforge4j-docs/build/sitemap.xml` (this feature's own git-derived-date fix),
// must survive verbatim through `assemble-site.mjs`'s composition into the public, root
// `_site/sitemap.xml` — a lastmod that only ever exists in the intermediate docs build and vanishes
// at composition never reaches a search engine, which makes this feature pointless without this
// check. Deliberately reuses the real `assembleSite` rather than an independent sitemap parser of
// this feature's own: `assemble-site.mjs`'s sitemap merge (`mergeSitemaps`) is what preserves
// `<lastmod>`, and it is owned elsewhere — this file asserts only the observable end-to-end
// guarantee against the real, current composition path, so that a change on either side of the
// boundary is caught here rather than shipping a sitemap the dates never reached.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { assembleSite } from './assemble-site.mjs';

const DOCS_LASTMOD = '2026-07-15';
const DOCS_URL = 'https://agentforge4j.org/docs/0.1.0/';

// Minimal shape `javadoc-seo.mjs`'s `injectJavadocPageSeo` requires of every raw javadoc page it
// processes (real `<head>`/`</head>`, an `<html lang>` attribute, and a description meta tag) —
// matches `assemble-site.test.mjs`'s own `realisticJavadocHtml` fixture shape, since this file's
// javadoc fixture is now run through that same real post-processing step during `assembleSite`.
function realisticJavadocHtml(marker) {
  return (
    '<!DOCTYPE HTML>\n<html lang>\n<head>\n<title>Overview</title>\n' +
    '<meta name="description" content="module index">\n</head>\n' +
    `<body>${marker}</body>\n</html>\n`
  );
}

function sitemapXmlFixture(entries) {
  const body = entries
    .map(
      ({ url, lastmod }) =>
        `  <url>\n    <loc>${url}</loc>${lastmod ? `\n    <lastmod>${lastmod}</lastmod>` : ''}\n  </url>`,
    )
    .join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    `${body}\n</urlset>\n`
  );
}

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'docs-lastmod-composition-'));
  const spaDir = join(root, 'spa');
  const buildDir = join(root, 'build');
  const javadocDir = join(root, 'javadoc-next');
  mkdirSync(spaDir, { recursive: true });
  writeFileSync(join(spaDir, 'index.html'), '<html>spa</html>');
  writeFileSync(join(spaDir, '404.html'), '<html>spa</html>');
  writeFileSync(join(spaDir, 'robots.txt'), 'User-agent: *\nAllow: /\n\nSitemap: https://agentforge4j.org/sitemap.xml\n');
  writeFileSync(join(spaDir, 'sitemap.xml'), sitemapXmlFixture([{ url: 'https://agentforge4j.org/' }]));
  mkdirSync(buildDir, { recursive: true });
  // The real docs build's root page is the redirects plugin's client-redirect stub — see the same
  // note in assemble-site.test.mjs's own fixture.
  writeFileSync(
    join(buildDir, 'index.html'),
    '<!DOCTYPE html><html><head><meta charset="UTF-8">' +
      '<meta http-equiv="refresh" content="0; url=/docs/0.1.0/">' +
      '<link rel="canonical" href="/docs/0.1.0/" /></head></html>',
  );
  // Stands in for a real Docusaurus `lastmod: 'date'` sitemap-plugin postBuild output, with
  // sufficient git history to have produced a real per-page date (this feature's own concern).
  writeFileSync(join(buildDir, 'sitemap.xml'), sitemapXmlFixture([{ url: DOCS_URL, lastmod: DOCS_LASTMOD }]));
  mkdirSync(javadocDir, { recursive: true });
  writeFileSync(join(javadocDir, 'index.html'), realisticJavadocHtml('javadoc'));
  return { spaDir, buildDir, javadocDir, archiveDir: join(root, 'archive-absent'), siteDir: join(root, '_site') };
}

test("a docs sitemap entry's <lastmod> survives composition into the final, public _site/sitemap.xml", () => {
  const { spaDir, buildDir, javadocDir, archiveDir, siteDir } = fixture();
  assembleSite({ spaDir, buildDir, javadocDir, archiveDir, siteDir, customDomain: null });

  const composed = readFileSync(join(siteDir, 'sitemap.xml'), 'utf8');
  const escapedUrl = DOCS_URL.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = new RegExp(`<url>\\s*<loc>${escapedUrl}</loc>\\s*<lastmod>([^<]+)</lastmod>\\s*</url>`).exec(composed);
  assert.ok(
    match,
    `composed _site/sitemap.xml carries no <lastmod> at all for ${DOCS_URL} — the docs build's own ` +
      `<lastmod> was dropped during composition. Full composed sitemap.xml:\n${composed}`,
  );
  assert.equal(
    match[1],
    DOCS_LASTMOD,
    `composed _site/sitemap.xml's <lastmod> for ${DOCS_URL} is "${match[1]}", expected the docs build's own "${DOCS_LASTMOD}"`,
  );
});

// SPDX-License-Identifier: Apache-2.0
//
// Regression assertion owned by the docs-lastmod feature (AF4J-178-01): a docs page's <lastmod>,
// once present in `agentforge4j-docs/build/sitemap.xml` (this feature's own git-derived-date fix),
// must survive verbatim through `assemble-site.mjs`'s composition into the public, root
// `_site/sitemap.xml` — a lastmod that only ever exists in the intermediate docs build and vanishes
// at composition never reaches a search engine, which makes this feature pointless without this
// check. Deliberately reuses the real `assembleSite` rather than an independent sitemap parser of
// this feature's own — sitemap composition/preservation itself is owned by the sitemap-hardening
// track, not this one; this file only asserts the observable end-to-end guarantee.
//
// `assemble-site.mjs`'s sitemap merge (`mergeSitemaps`) now preserves `<lastmod>` — that fix
// landed via the separate sitemap-hardening branch (main, then rebased onto here) — so this test
// exercises the real, current composition path end to end.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { assembleSite } from './assemble-site.mjs';

const DOCS_LASTMOD = '2026-07-15';
const DOCS_URL = 'https://agentforge4j.org/docs/0.1.0/';

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
  writeFileSync(join(buildDir, 'index.html'), '<html>docs</html>');
  // Stands in for a real Docusaurus `lastmod: 'date'` sitemap-plugin postBuild output, with
  // sufficient git history to have produced a real per-page date (this feature's own concern).
  writeFileSync(join(buildDir, 'sitemap.xml'), sitemapXmlFixture([{ url: DOCS_URL, lastmod: DOCS_LASTMOD }]));
  mkdirSync(javadocDir, { recursive: true });
  writeFileSync(join(javadocDir, 'index.html'), '<html>javadoc</html>');
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

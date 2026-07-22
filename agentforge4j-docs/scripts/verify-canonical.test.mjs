// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for verify-canonical.mjs against fixture build/ directories standing in for a real
// docusaurus build (mirrors verify-noindex.test.mjs's own fixture approach) — no real build required.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { verifyCanonicalTrailingSlash } from './verify-canonical.mjs';

function pageWithCanonical(canonical) {
  return `<!doctype html><html><head><title>x</title><link data-rh=true rel=canonical href=${canonical} /></head><body></body></html>`;
}

function writePage(buildDir, relPath, canonical) {
  const full = join(buildDir, ...relPath.split('/'));
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, pageWithCanonical(canonical));
}

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'verify-canonical-'));
  return join(root, 'build');
}

test('passes clean when every canonical and every sitemap URL ends in a trailing slash, each with a valid lastmod', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writePage(buildDir, '0.1.0/get-started/quick-start/index.html', 'https://agentforge4j.org/docs/0.1.0/get-started/quick-start/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url>' +
      '<url><loc>https://agentforge4j.org/docs/0.1.0/get-started/quick-start/</loc><lastmod>2026-07-21</lastmod></url></urlset>',
  );
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir }));
});

test('fails closed on a non-trailing-slash canonical (the exact pre-fix defect: it 301-redirects on GitHub Pages)', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0'); // no trailing slash
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /does not end in '\/'/);
});

test('fails closed on a non-trailing-slash sitemap URL', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /sitemap URL .* does not end in/);
});

test('fails closed on a missing or invalid <lastmod>', () => {
  const buildDir = fixture();
  writePage(buildDir, '0.1.0/index.html', 'https://agentforge4j.org/docs/0.1.0/');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc></url></urlset>',
  );
  assert.throws(() => verifyCanonicalTrailingSlash({ buildDir }), /no valid <lastmod>/);
});

test('a page with no canonical tag at all (e.g. the search results page) is not itself a failure', () => {
  const buildDir = fixture();
  const searchPage = join(buildDir, 'search', 'index.html');
  mkdirSync(dirname(searchPage), { recursive: true });
  writeFileSync(searchPage, '<!doctype html><html><head><title>Search</title></head><body></body></html>');
  writeFileSync(
    join(buildDir, 'sitemap.xml'),
    '<urlset><url><loc>https://agentforge4j.org/docs/0.1.0/</loc><lastmod>2026-07-20</lastmod></url></urlset>',
  );
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir }));
});

test('archive-mode builds are skipped entirely (no moving alias / canonical policy of their own)', () => {
  const buildDir = fixture();
  mkdirSync(buildDir, { recursive: true });
  assert.doesNotThrow(() => verifyCanonicalTrailingSlash({ buildDir, archiveVersion: '1.0.0' }));
});

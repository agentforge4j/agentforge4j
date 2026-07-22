// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for verify-seo.mjs against fixture dist/ directories standing in for a real build
// — each spins up the real GitHub-Pages-emulating server against a small hand-built fixture, so
// these exercise the real HTTP round-trip logic (not just string matching), without requiring a
// real `vite build` + prerender pass. `requiredRoutes: []` in every fixture below opts out of the
// production route list (which real fixtures here do not have pages for) without weakening what
// each test is actually checking — the sitemap-driven checks run unconditionally either way.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { verifySeo } from './verify-seo.mjs';

function page({ h1 = '<h1>Real Title</h1>', canonical, extraHead = '' } = {}) {
  return (
    `<!doctype html><html lang="en"><head><meta charset="UTF-8">` +
    `<link rel="canonical" href="${canonical}" />${extraHead}</head>` +
    `<body>${h1}</body></html>`
  );
}

function writePage(distDir, relDir, html) {
  const dir = relDir ? join(distDir, ...relDir.split('/')) : distDir;
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'index.html'), html, 'utf8');
}

function sitemapXml(entries) {
  const body = entries
    .map(({ url, lastmod }) => `<url><loc>${url}</loc>${lastmod ? `<lastmod>${lastmod}</lastmod>` : ''}</url>`)
    .join('');
  return `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${body}</urlset>`;
}

function fixtureDir() {
  return mkdtempSync(join(tmpdir(), 'verify-seo-'));
}

test('passes clean on a well-formed fixture: trailing-slash sitemap URLs, matching self-canonical, one real <h1>, valid lastmod', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(distDir, 'api', page({ canonical: 'https://agentforge4j.org/api/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
      { url: 'https://agentforge4j.org/api/', lastmod: '2026-07-21' },
    ]),
    'utf8',
  );
  await assert.doesNotReject(() => verifySeo({ distDir, requiredRoutes: [] }));
});

test('fails closed when a sitemap URL is the pre-fix non-slash form (the original bug this pass fixes: it 301s instead of returning 200 directly)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(distDir, 'api', page({ canonical: 'https://agentforge4j.org/api' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: null },
      { url: 'https://agentforge4j.org/api', lastmod: null }, // bare, non-slash — the regressed form
    ]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: [] }), /did not return 200 with no redirect/);
});

test('fails closed when a page\'s own canonical tag does not match its sitemap URL exactly', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/wrong/' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: null }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: [] }), /does not match its own sitemap URL/);
});

test('fails closed on more than one <h1> in the raw served HTML', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/', h1: '<h1>One</h1><h1>Two</h1>' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: null }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: [] }), /expected exactly one <h1>/);
});

test('fails closed on zero <h1> in the raw served HTML (the exact pre-fix Bing "H1 tag missing" defect)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/', h1: '<div id="root"></div>' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: null }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: [] }), /expected exactly one <h1>/);
});

test('fails closed on an invalid (non-W3C-date) <lastmod>', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: 'not-a-date' }]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: [] }), /not a valid W3C date/);
});

test('fails closed on a duplicate URL in the real sitemap.xml', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: null },
      { url: 'https://agentforge4j.org/', lastmod: null },
    ]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: [] }), /duplicate URL/);
});

test('a required route missing real visible <h1> text fails closed even if the sitemap-driven checks alone would have passed', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/', h1: '<h1></h1>' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: null }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, requiredRoutes: ['/'] }), /no real visible text content/);
});

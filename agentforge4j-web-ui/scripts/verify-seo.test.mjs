// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for verify-seo.mjs against fixture dist/ directories standing in for a real build
// — each spins up the real GitHub-Pages-emulating server against a small hand-built fixture, so
// these exercise the real HTTP round-trip logic (not just string matching), without requiring a
// real `vite build` + prerender pass. `staticRoutes: []` in every fixture below opts out of the
// real seo-routes.json-derived inventory (which real fixtures here do not have pages for) without
// weakening what each test is actually checking — the sitemap-driven checks run unconditionally
// either way.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { request } from 'node:http';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadStaticRouteInventory, resolveWithinRoot, startGhPagesEmulatingServer, verifySeo } from './verify-seo.mjs';
import { JSON_LD_SCRIPT_ID } from './build-seo.mjs';

const REAL_MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

/** A raw HTTP GET that sends `rawPath` on the request line completely unnormalized — unlike
 * `fetch()`/undici, which parses the URL through the WHATWG URL parser and silently collapses `..`
 * segments (and `%2e%2e` once decoded) BEFORE the request is ever sent, so a `fetch('.../../x')`
 * traversal test would never actually reach the server with a `..` in `req.url` at all — it would
 * pass even against the un-fixed, vulnerable code, proving nothing. `http.request`'s `path` option
 * is sent to the wire exactly as given, so this is the only way to prove the server's own
 * containment check — not the HTTP client's leniency — is what rejects a traversal attempt. */
function rawGet(port, rawPath) {
  return new Promise((resolvePromise, reject) => {
    const req = request({ host: '127.0.0.1', port, path: rawPath, method: 'GET' }, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolvePromise({ status: res.statusCode, body: Buffer.concat(chunks).toString('utf8') }));
    });
    req.on('error', reject);
    req.end();
  });
}

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
  const distDir = mkdtempSync(join(tmpdir(), 'verify-seo-'));
  // Every fixture ships a valid 404 catch-all by default — the empty pre-prerender SPA shell
  // copy-404.mjs produces on a real build. The dedicated 404-gate tests below overwrite or
  // remove it deliberately.
  writeFileSync(
    join(distDir, '404.html'),
    '<!doctype html><html lang="en"><head><meta charset="UTF-8"></head><body><div id="root"></div></body></html>',
    'utf8',
  );
  return distDir;
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
  await assert.doesNotReject(() => verifySeo({ distDir, staticRoutes: [] }));
});

test('fails closed when a sitemap URL is the pre-fix non-slash form (the original bug this pass fixes: it 301s instead of returning 200 directly)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(distDir, 'api', page({ canonical: 'https://agentforge4j.org/api' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
      { url: 'https://agentforge4j.org/api', lastmod: '2026-07-20' }, // bare, non-slash — the regressed form
    ]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /did not return 200 with no redirect/);
});

test('fails closed when a page\'s own canonical tag does not match its sitemap URL exactly', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/wrong/' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /does not match its own sitemap URL/);
});

test('fails closed on more than one <h1> in the raw served HTML', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/', h1: '<h1>One</h1><h1>Two</h1>' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /expected exactly one <h1>/);
});

test('fails closed on zero <h1> in the raw served HTML (the exact pre-fix Bing "H1 tag missing" defect)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/', h1: '<div id="root"></div>' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /expected exactly one <h1>/);
});

test('fails closed on a missing <lastmod> (a production sitemap where every date disappeared must never pass)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: null }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /has no <lastmod>/);
});

test('fails closed on a duplicate <lastmod> tag inside one <url> block', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    '<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' +
      '<url><loc>https://agentforge4j.org/</loc><lastmod>2026-07-20</lastmod><lastmod>2026-07-21</lastmod></url>' +
      '</urlset>',
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /has 2 <lastmod> tags — expected exactly one/);
});

test('fails closed on an invalid (non-W3C-shaped) <lastmod>', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: 'not-a-date' }]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /not a valid real calendar date/);
});

test('fails closed on a regex-shaped but impossible calendar date (2026-02-31)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-02-31' }]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /not a valid real calendar date/);
});

test('accepts a real leap day (2024-02-29) but rejects the same day in a non-leap year (2026-02-29)', async () => {
  const leapDistDir = fixtureDir();
  writePage(leapDistDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(join(leapDistDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2024-02-29' }]), 'utf8');
  await assert.doesNotReject(() => verifySeo({ distDir: leapDistDir, staticRoutes: [] }));

  const nonLeapDistDir = fixtureDir();
  writePage(nonLeapDistDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(join(nonLeapDistDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-02-29' }]), 'utf8');
  await assert.rejects(() => verifySeo({ distDir: nonLeapDistDir, staticRoutes: [] }), /not a valid real calendar date/);
});

test('fails closed on a duplicate URL in the real sitemap.xml', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
    ]),
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /duplicate URL/);
});

test('a configured static route missing real visible <h1> text fails closed even if the sitemap-driven checks alone would have passed', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/', h1: '<h1></h1>' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' }] }),
    /no real visible text content/,
  );
});

// --- The 404 catch-all gate: dist/404.html must stay the empty pre-prerender SPA shell. If the
// build ever wrote it from the post-prerender index.html (the copy-404-after-build-seo ordering
// this gate exists to prevent), every mistyped URL would statically display the full home page
// body under a real HTTP 404. ---

test('fails closed when dist/404.html carries prerendered body content instead of the empty mount point', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  writeFileSync(
    join(distDir, '404.html'),
    '<!doctype html><html lang="en"><head></head><body><div id="root"><h1>Home</h1><main>full prerendered home body</main></div></body></html>',
    'utf8',
  );
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /404\.html no longer contains an empty/);
});

test('fails closed when dist/404.html is missing entirely (the 404 catch-all is part of the verified artifact)', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  rmSync(join(distDir, '404.html'));
  await assert.rejects(() => verifySeo({ distDir, staticRoutes: [] }), /404\.html does not exist/);
});

// --- JSON-LD: verified against the real, actually-served dist/ output (not a fixture standing in
// for it — see build-seo.test.mjs's own note pointing here), the same "check the real build output
// directly" philosophy every other check in this file already follows. ---

test('a configured route whose real served HTML carries a JSON-LD script matching its declared config passes clean', async () => {
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

test('fails closed when a route\'s served JSON-LD script has no id at all — it would hydrate into a duplicate, not an update, on the client', async () => {
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({ canonical: 'https://agentforge4j.org/', extraHead: `<script type="application/ld+json">${JSON.stringify(jsonLd)}</script>` }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /has id null — expected/,
  );
});

test('fails closed when a route\'s served JSON-LD script has the wrong id — the client-side hook would never find it', async () => {
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({ canonical: 'https://agentforge4j.org/', extraHead: `<script id="wrong-id" type="application/ld+json">${JSON.stringify(jsonLd)}</script>` }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /has id "wrong-id" — expected/,
  );
});

test('the id-attribute check tolerates attribute order — id before or after the type attribute both match', async () => {
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script type="application/ld+json" id="${JSON_LD_SCRIPT_ID}">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

test('fails closed when a route declares jsonLd but its real served HTML has none at all', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () =>
      verifySeo({
        distDir,
        staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd: { '@context': 'https://schema.org', '@type': 'WebSite' } }],
      }),
    /has 0 JSON-LD script\(s\) — expected exactly 1/,
  );
});

test('fails closed when a route\'s served JSON-LD does not match its declared seo-routes.json config (a stale/mismatched build)', async () => {
  const distDir = fixtureDir();
  const served = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'Wrong Name' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(served)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  const declared = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd: declared }] }),
    /does not match its declared seo-routes\.json config/,
  );
});

test('fails closed when a route declares no jsonLd but its real served HTML unexpectedly carries one (leaked from another route)', async () => {
  const distDir = fixtureDir();
  const leaked = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    'architecture',
    page({ canonical: 'https://agentforge4j.org/architecture/', extraHead: `<script type="application/ld+json">${JSON.stringify(leaked)}</script>` }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/architecture/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/architecture/', expectedCanonical: 'https://agentforge4j.org/architecture/' }] }),
    /has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

// --- The stray-JSON-LD check recognises every spelling a browser honours, not only the exact one
// build-seo.mjs emits. Its whole job is catching structured data from a producer OTHER than
// injectJsonLd, so a matcher anchored to injectJsonLd's own output would be blind to precisely the
// cases it exists for. ---

test('fails closed when a leaked JSON-LD script uses single quotes around its type attribute', async () => {
  const distDir = fixtureDir();
  const leaked = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    'architecture',
    page({
      canonical: 'https://agentforge4j.org/architecture/',
      extraHead: `<script type='application/ld+json'>${JSON.stringify(leaked)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/architecture/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/architecture/', expectedCanonical: 'https://agentforge4j.org/architecture/' }] }),
    /has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

test('fails closed when a leaked JSON-LD script spells its media type in a different case (media types are case-insensitive)', async () => {
  const distDir = fixtureDir();
  const leaked = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    'architecture',
    page({
      canonical: 'https://agentforge4j.org/architecture/',
      extraHead: `<script type="application/LD+JSON">${JSON.stringify(leaked)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/architecture/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/architecture/', expectedCanonical: 'https://agentforge4j.org/architecture/' }] }),
    /has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

test('fails closed when a leaked JSON-LD script leaves its type attribute value UNQUOTED', async () => {
  const distDir = fixtureDir();
  const leaked = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    'architecture',
    page({
      canonical: 'https://agentforge4j.org/architecture/',
      // An unquoted attribute value is ordinary HTML — a parser resolves this to exactly the same
      // `type` as the double-quoted spelling, so it renders as real structured data.
      extraHead: `<script type=application/ld+json>${JSON.stringify(leaked)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/architecture/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/architecture/', expectedCanonical: 'https://agentforge4j.org/architecture/' }] }),
    /has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

test('fails closed when a leaked JSON-LD script puts whitespace around the type attribute\'s "="', async () => {
  const distDir = fixtureDir();
  const leaked = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    'architecture',
    page({
      canonical: 'https://agentforge4j.org/architecture/',
      extraHead: `<script type = "application/ld+json">${JSON.stringify(leaked)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/architecture/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/architecture/', expectedCanonical: 'https://agentforge4j.org/architecture/' }] }),
    /has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

test('fails closed when a leaked JSON-LD script pads its media type with whitespace (the HTML spec strips it before classifying the block)', async () => {
  const distDir = fixtureDir();
  const leaked = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    'architecture',
    page({
      canonical: 'https://agentforge4j.org/architecture/',
      extraHead: `<script type=" application/ld+json ">${JSON.stringify(leaked)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/architecture/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/architecture/', expectedCanonical: 'https://agentforge4j.org/architecture/' }] }),
    /has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

test('a stray JSON-LD block on a non-configured sitemap URL is caught in the widened spellings too, not only on configured routes', async () => {
  // Both leak checks share one extractor, so this proves the widened matcher reaches the second
  // caller — the catalogue detail shells nothing else covers — rather than only the first.
  const distDir = fixtureDir();
  const stray = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(
    distDir,
    'catalogue/agent-creator',
    page({
      canonical: 'https://agentforge4j.org/catalogue/agent-creator/',
      extraHead: `<script type=application/ld+json>${JSON.stringify(stray)}</script>`,
    }),
  );
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
      { url: 'https://agentforge4j.org/catalogue/agent-creator/', lastmod: '2026-07-20' },
    ]),
    'utf8',
  );
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' }] }),
    /catalogue\/agent-creator\/ has 1 JSON-LD script\(s\) but declares no jsonLd/,
  );
});

test('a data-id attribute is not mistaken for the script\'s own id — the shell would ship an id the client-side hook cannot find', async () => {
  // A plain `\bid=` matches inside `data-id=`, because `-` is a word boundary. Reading that as the
  // script's id would report a shell with NO usable id as correctly identified, shipping exactly
  // the duplicate-on-hydration failure the id check exists to prevent.
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script data-id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /has id null — expected/,
  );
});

test('an id padded with whitespace is rejected rather than silently trimmed to a match — getElementById would not find it', async () => {
  // The type value IS stripped before comparison (the HTML spec strips it before classifying the
  // block); an id is not. `id=" seo-json-ld "` really is an id containing spaces, and
  // `document.getElementById('seo-json-ld')` does not find it, so trimming here would wave through
  // exactly the shell this check exists to reject.
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id=" ${JSON_LD_SCRIPT_ID} " type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /has id " seo-json-ld " — expected/,
  );
});

test('the shared-id check reads an UNQUOTED id attribute too, rather than reporting it as absent', async () => {
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id=${JSON_LD_SCRIPT_ID} type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

test('the shared-id check reads a single-quoted id attribute too, rather than reporting it as absent', async () => {
  const distDir = fixtureDir();
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id='${JSON_LD_SCRIPT_ID}' type='application/ld+json'>${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

// --- Same-origin assets a route's structured data names (logo/image) must actually be in the
// build. Nothing else notices a renamed or removed target: the build-time origin guard only asks
// whose host it is, assertValidJsonLd never inspects it, and the served-vs-declared comparison is
// satisfied by two copies of the same dead URL. ---

test('fails closed when a route\'s JSON-LD names a same-origin logo this build does not serve', async () => {
  const distDir = fixtureDir();
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    logo: 'https://agentforge4j.org/brand/icon-512.png',
  };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  // No brand/icon-512.png written at all.
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /points at \/brand\/icon-512\.png, which this build does not serve \(got 404\)/,
  );
});

test('passes clean when the named same-origin asset is really in the build, and reads a root-relative value too', async () => {
  const distDir = fixtureDir();
  const jsonLd = {
    '@context': 'https://schema.org',
    '@graph': [
      { '@type': 'Organization', logo: 'https://agentforge4j.org/brand/icon-512.png' },
      // A root-relative reference resolves to the same origin and must be checked identically.
      { '@type': 'WebPage', image: '/brand/social.png' },
    ],
  };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  mkdirSync(join(distDir, 'brand'), { recursive: true });
  writeFileSync(join(distDir, 'brand', 'icon-512.png'), 'not-really-a-png', 'utf8');
  writeFileSync(join(distDir, 'brand', 'social.png'), 'not-really-a-png', 'utf8');
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

test('fails closed when an ImageObject-form logo names a same-origin file this build does not serve', async () => {
  // `logo`/`image` as a full ImageObject node (rather than a bare URL string) is the more idiomatic
  // schema.org spelling and the likeliest next edit to this config. Reading only the bare string
  // would skip the asset silently — the check would report clean on a logo that 404s, which is the
  // whole failure it exists to catch.
  const distDir = fixtureDir();
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    logo: { '@type': 'ImageObject', url: 'https://agentforge4j.org/brand/icon-512.png', width: 512, height: 512 },
  };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  // No brand/icon-512.png written at all.
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /points at \/brand\/icon-512\.png, which this build does not serve \(got 404\)/,
  );
});

test('fails closed on a missing ImageObject nested INSIDE an array-valued image — the array and object shapes compose', async () => {
  // Deliberately a rejection rather than a "passes clean" case. A test that only asserts the happy
  // path cannot distinguish "the nested ImageObject was fetched and served" from "it was never
  // looked at": both produce no error. Naming the missing file in the failure is the only thing
  // that proves this shape is actually walked.
  const distDir = fixtureDir();
  const jsonLd = {
    '@context': 'https://schema.org',
    '@graph': [
      { '@type': 'Organization', logo: { '@type': 'ImageObject', url: 'https://agentforge4j.org/brand/icon-512.png' } },
      { '@type': 'WebPage', image: ['/brand/social.png', { '@type': 'ImageObject', url: '/brand/wide.png' }] },
    ],
  };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  mkdirSync(join(distDir, 'brand'), { recursive: true });
  // Everything the block names EXCEPT the nested ImageObject's own target.
  for (const file of ['icon-512.png', 'social.png']) {
    writeFileSync(join(distDir, 'brand', file), 'not-really-a-png', 'utf8');
  }
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
    /points at \/brand\/wide\.png, which this build does not serve \(got 404\)/,
  );
});

test('passes clean once every ImageObject-form and array-valued asset really is in the build (no false positives from the widened shapes)', async () => {
  // The companion to the two rejection cases above: widening which shapes are read must not start
  // failing a build whose assets are all present. This one cannot prove the shapes are walked —
  // only that walking them is not over-eager — which is why it is not the only coverage.
  const distDir = fixtureDir();
  const jsonLd = {
    '@context': 'https://schema.org',
    '@graph': [
      { '@type': 'Organization', logo: { '@type': 'ImageObject', url: 'https://agentforge4j.org/brand/icon-512.png' } },
      { '@type': 'WebPage', image: ['/brand/social.png', { '@type': 'ImageObject', url: '/brand/wide.png' }] },
    ],
  };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  mkdirSync(join(distDir, 'brand'), { recursive: true });
  for (const file of ['icon-512.png', 'social.png', 'wide.png']) {
    writeFileSync(join(distDir, 'brand', file), 'not-really-a-png', 'utf8');
  }
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

test('an off-origin asset is skipped rather than fetched — a local build gate must not depend on a third party\'s uptime', async () => {
  const distDir = fixtureDir();
  // A deliberately external image on a host this test could never reach. If the check fetched it
  // instead of skipping it, this test would fail (or hang) rather than pass.
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'WebPage',
    image: 'https://images.invalid.example/never-resolvable.png',
  };
  writePage(
    distDir,
    '',
    page({
      canonical: 'https://agentforge4j.org/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(jsonLd)}</script>`,
    }),
  );
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/', jsonLd }] }),
  );
});

// --- A sitemap URL that is not a configured static route — every /catalogue/<id>/ detail shell —
// can only ever legitimately carry zero structured data. Nothing but this check would notice if one
// started carrying some, since the per-route loop below only ever sees seo-routes.json's own
// routes. ---

test('fails closed when a sitemap URL that is not a configured static route (e.g. a catalogue detail shell) carries structured data', async () => {
  const distDir = fixtureDir();
  const stray = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(
    distDir,
    'catalogue/agent-creator',
    page({
      canonical: 'https://agentforge4j.org/catalogue/agent-creator/',
      extraHead: `<script id="${JSON_LD_SCRIPT_ID}" type="application/ld+json">${JSON.stringify(stray)}</script>`,
    }),
  );
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
      { url: 'https://agentforge4j.org/catalogue/agent-creator/', lastmod: '2026-07-20' },
    ]),
    'utf8',
  );
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' }] }),
    /catalogue\/agent-creator\/ has 1 JSON-LD script\(s\) but declares no jsonLd[\s\S]*only a configured static route may carry structured data/,
  );
});

test('a catalogue-shaped sitemap URL with no structured data passes clean — the stray check is not a blanket rejection', async () => {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(distDir, 'catalogue/agent-creator', page({ canonical: 'https://agentforge4j.org/catalogue/agent-creator/' }));
  writeFileSync(
    join(distDir, 'sitemap.xml'),
    sitemapXml([
      { url: 'https://agentforge4j.org/', lastmod: '2026-07-20' },
      { url: 'https://agentforge4j.org/catalogue/agent-creator/', lastmod: '2026-07-20' },
    ]),
    'utf8',
  );
  await assert.doesNotReject(() =>
    verifySeo({ distDir, staticRoutes: [{ requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' }] }),
  );
});

// --- loadStaticRouteInventory: the real per-route verification list, derived from the committed
// seo-routes.json rather than a hand-maintained subset — this is what makes a sitemap: false alias
// route like /contributing actually get checked at all, since the sitemap-driven loop above never
// sees it (it is deliberately excluded from the sitemap). ---

test('loadStaticRouteInventory derives every route from the real committed seo-routes.json, including the sitemap: false /contributing alias', () => {
  const { routes } = JSON.parse(readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'));
  const inventory = loadStaticRouteInventory(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'));
  assert.equal(inventory.length, routes.length, 'every configured route must be in the inventory, sitemap: false or not');
  const contributing = inventory.find((entry) => entry.requestPath === '/contributing/');
  assert.ok(contributing, 'expected /contributing/ (sitemap: false) to still be part of the real static-route inventory');
  assert.equal(
    contributing.expectedCanonical,
    'https://agentforge4j.org/community/',
    '/contributing declares canonicalPath: "/community" — its expected canonical must be the alias target, trailing-slash form',
  );
  // A route with no canonicalPath expects its own trailing-slash URL.
  const architecture = inventory.find((entry) => entry.requestPath === '/architecture/');
  assert.ok(architecture);
  assert.equal(architecture.expectedCanonical, 'https://agentforge4j.org/architecture/');
});

test('loadStaticRouteInventory normalizes every requestPath to its trailing-slash served form', () => {
  const root = mkdtempSync(join(tmpdir(), 'seo-routes-fixture-'));
  const seoRoutesPath = join(root, 'seo-routes.json');
  writeFileSync(
    seoRoutesPath,
    JSON.stringify({
      siteUrl: 'https://agentforge4j.org',
      routes: [
        { path: '/' },
        { path: '/api' },
        { path: '/contributing', canonicalPath: '/community' },
      ],
    }),
    'utf8',
  );
  assert.deepEqual(loadStaticRouteInventory(seoRoutesPath), [
    { requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' },
    { requestPath: '/api/', expectedCanonical: 'https://agentforge4j.org/api/' },
    { requestPath: '/contributing/', expectedCanonical: 'https://agentforge4j.org/community/' },
  ]);
});

test('loadStaticRouteInventory carries jsonLd for a route that declares one, and omits the key entirely for a route that does not', () => {
  const root = mkdtempSync(join(tmpdir(), 'seo-routes-fixture-jsonld-'));
  const seoRoutesPath = join(root, 'seo-routes.json');
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  writeFileSync(
    seoRoutesPath,
    JSON.stringify({
      siteUrl: 'https://agentforge4j.org',
      routes: [{ path: '/', jsonLd }, { path: '/api' }],
    }),
    'utf8',
  );
  const inventory = loadStaticRouteInventory(seoRoutesPath);
  assert.deepEqual(inventory[0].jsonLd, jsonLd);
  assert.ok(!('jsonLd' in inventory[1]), '/api declares no jsonLd — the key must be entirely absent, not present as undefined');
});

test('loadStaticRouteInventory reflects the real committed seo-routes.json: only "/" declares jsonLd today', () => {
  const inventory = loadStaticRouteInventory(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'));
  const home = inventory.find((entry) => entry.requestPath === '/');
  assert.ok(home?.jsonLd, 'expected the real "/" route to declare a jsonLd config');
  const architecture = inventory.find((entry) => entry.requestPath === '/architecture/');
  assert.ok(!('jsonLd' in architecture), '/architecture declares no jsonLd in the real config');
});

// --- End-to-end: a sitemap: false alias route (modelled on the real /contributing) is verified by
// the default (no override) code path — proving the fix structurally, not merely that a hardcoded
// path string was added — and a broken/empty shell for it fails the gate with a clear message. ---

function fixtureDirWithAliasRoute({ contributingCanonical = 'https://agentforge4j.org/community/', contributingH1 = '<h1>Community</h1>' } = {}) {
  const distDir = fixtureDir();
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  writePage(distDir, 'contributing', page({ canonical: contributingCanonical, h1: contributingH1 }));
  // /contributing is deliberately absent from the sitemap — sitemap: false in the real config.
  writeFileSync(join(distDir, 'sitemap.xml'), sitemapXml([{ url: 'https://agentforge4j.org/', lastmod: '2026-07-20' }]), 'utf8');
  return distDir;
}

test('a sitemap: false alias route is still verified via the real seo-routes.json-derived inventory (passes when its shell is correct)', async () => {
  const distDir = fixtureDirWithAliasRoute();
  const staticRoutes = [
    { requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' },
    { requestPath: '/contributing/', expectedCanonical: 'https://agentforge4j.org/community/' },
  ];
  await assert.doesNotReject(() => verifySeo({ distDir, staticRoutes }));
});

test('a sitemap: false alias route with the wrong canonical fails the gate with a clear route + expected/actual message', async () => {
  const distDir = fixtureDirWithAliasRoute({ contributingCanonical: 'https://agentforge4j.org/contributing/' });
  const staticRoutes = [
    { requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' },
    { requestPath: '/contributing/', expectedCanonical: 'https://agentforge4j.org/community/' },
  ];
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes }),
    /\/contributing\/ — expected canonical "https:\/\/agentforge4j\.org\/community\/", got "https:\/\/agentforge4j\.org\/contributing\/"/,
  );
});

test('a sitemap: false alias route with an empty/broken shell (no real <h1>) fails the gate', async () => {
  const distDir = fixtureDirWithAliasRoute({ contributingH1: '<div id="root"></div>' });
  const staticRoutes = [
    { requestPath: '/', expectedCanonical: 'https://agentforge4j.org/' },
    { requestPath: '/contributing/', expectedCanonical: 'https://agentforge4j.org/community/' },
  ];
  await assert.rejects(
    () => verifySeo({ distDir, staticRoutes }),
    /\/contributing\/ — expected exactly one <h1> in the raw served HTML, found 0/,
  );
});

// --- resolveWithinRoot: path-containment guard for the GH-Pages-emulating server's own request
// handling (a bare `candidate.startsWith(distDir)` check would incorrectly pass a sibling directory
// sharing the same string prefix, e.g. `dist-evil` next to `dist`) ---

test('resolveWithinRoot: a normal relative path resolves inside the root', () => {
  const root = join(tmpdir(), 'containment-root');
  assert.equal(resolveWithinRoot(root, '/index.html'), join(root, 'index.html'));
  assert.equal(resolveWithinRoot(root, '/'), root);
});

test('resolveWithinRoot: a `../` traversal segment is rejected (returns null), however deep', () => {
  const root = join(tmpdir(), 'containment-root');
  assert.equal(resolveWithinRoot(root, '/../secret.txt'), null);
  assert.equal(resolveWithinRoot(root, '/../../../../etc/passwd'), null);
});

test('resolveWithinRoot: an already-decoded traversal segment is rejected identically (the server decodeURIComponents the URL before calling this)', () => {
  const root = join(tmpdir(), 'containment-root');
  assert.equal(resolveWithinRoot(root, decodeURIComponent('/%2e%2e%2fsecret.txt')), null);
});

test('resolveWithinRoot: a sibling directory sharing the same string prefix is rejected, not treated as a child', () => {
  const root = join(tmpdir(), 'dist');
  assert.equal(resolveWithinRoot(root, '/../dist-evil/secret.txt'), null);
});

// --- startGhPagesEmulatingServer: the same containment guard, exercised through a real HTTP
// round-trip with an unnormalized request path (see rawGet's own comment for why fetch() cannot
// be used to prove this) ---

function fixtureDirWithSibling() {
  const root = mkdtempSync(join(tmpdir(), 'verify-seo-containment-'));
  const distDir = join(root, 'dist');
  writePage(distDir, '', page({ canonical: 'https://agentforge4j.org/' }));
  const siblingDir = join(root, 'dist-evil');
  mkdirSync(siblingDir, { recursive: true });
  writeFileSync(join(siblingDir, 'secret.txt'), 'THIS MUST NEVER BE SERVED', 'utf8');
  return { distDir, siblingDir };
}

test('startGhPagesEmulatingServer serves a real file inside the root normally', async () => {
  const { distDir } = fixtureDirWithSibling();
  const server = await startGhPagesEmulatingServer(distDir);
  try {
    const { port } = server.address();
    const { status, body } = await rawGet(port, '/');
    assert.equal(status, 200);
    assert.match(body, /Real Title/);
  } finally {
    await new Promise((r) => server.close(r));
  }
});

test('startGhPagesEmulatingServer rejects (never serves) a file reached via `../` traversal', async () => {
  const { distDir, siblingDir } = fixtureDirWithSibling();
  assert.ok(existsSync(join(siblingDir, 'secret.txt')));
  const server = await startGhPagesEmulatingServer(distDir);
  try {
    const { port } = server.address();
    const { status, body } = await rawGet(port, '/../dist-evil/secret.txt');
    assert.notEqual(status, 200);
    assert.ok(!body.includes('THIS MUST NEVER BE SERVED'), 'the traversal-reached sibling file must never be served');
  } finally {
    await new Promise((r) => server.close(r));
  }
});

test('startGhPagesEmulatingServer rejects an encoded `../` traversal form identically', async () => {
  const { distDir, siblingDir } = fixtureDirWithSibling();
  assert.ok(existsSync(join(siblingDir, 'secret.txt')));
  const server = await startGhPagesEmulatingServer(distDir);
  try {
    const { port } = server.address();
    const { status, body } = await rawGet(port, '/%2e%2e/dist-evil/secret.txt');
    assert.notEqual(status, 200);
    assert.ok(!body.includes('THIS MUST NEVER BE SERVED'), 'the traversal-reached sibling file must never be served');
  } finally {
    await new Promise((r) => server.close(r));
  }
});

// --- Malformed percent-encoding: decodeURIComponent throws URIError on a truncated/invalid escape
// (e.g. a lone high surrogate byte with no continuation) — uncaught, that would crash the whole
// process taking every other in-flight check down with it. ---

test('startGhPagesEmulatingServer returns a controlled 400 for malformed percent-encoding, and stays alive to serve a valid request afterward', async () => {
  const { distDir } = fixtureDirWithSibling();
  const server = await startGhPagesEmulatingServer(distDir);
  try {
    const { port } = server.address();
    const malformed = await rawGet(port, '/%E0%A4%A');
    assert.equal(malformed.status, 400);

    // The server process must not have crashed: a real, valid request right after must still work.
    const valid = await rawGet(port, '/');
    assert.equal(valid.status, 200);
    assert.match(valid.body, /Real Title/);
  } finally {
    await new Promise((r) => server.close(r));
  }
});

// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for the per-route SEO shell + sitemap-fragment generator. Fixture dist/,
// seo-routes.json, and catalogue-data.json under a temp root stand in for a real build — no
// `vite build` required.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildSeo, escapeHtml, injectHead, withTrailingSlash } from './build-seo.mjs';
import { WORKFLOW_ID_PATTERN } from './workflow-id-contract.mjs';

const REAL_MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

const BASE_INDEX_HTML = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>AgentForge4j — Governed AI Workflows for Java</title>
    <meta
      name="description"
      content="AgentForge4j is an open-source Java framework for building governed AI workflows."
    />
    <link rel="canonical" href="https://agentforge4j.org/" />
    <meta property="og:type" content="website" />
    <meta property="og:url" content="https://agentforge4j.org/" />
    <meta property="og:title" content="AgentForge4j — Governed AI Workflows for Java" />
    <meta
      property="og:description"
      content="AgentForge4j is an open-source Java framework for building governed AI workflows."
    />
    <meta name="twitter:card" content="summary" />
    <meta name="twitter:title" content="AgentForge4j — Governed AI Workflows for Java" />
    <meta
      name="twitter:description"
      content="AgentForge4j is an open-source Java framework for building governed AI workflows."
    />
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>
`;

const SAMPLE_ROUTES = {
  siteUrl: 'https://agentforge4j.org',
  routes: [
    { path: '/', title: 'Home Title', description: 'Home description.' },
    { path: '/architecture', title: 'Architecture — AgentForge4j', description: 'Architecture description.' },
    {
      path: '/contributing',
      title: 'Contributing — AgentForge4j',
      description: 'Contributing description.',
      canonicalPath: '/community',
      sitemap: false,
    },
  ],
};

function fixture({ routes = SAMPLE_ROUTES, workflows = [] } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'build-seo-'));
  const distDir = join(root, 'dist');
  mkdirSync(distDir, { recursive: true });
  writeFileSync(join(distDir, 'index.html'), BASE_INDEX_HTML, 'utf8');

  const seoRoutesPath = join(root, 'seo-routes.json');
  writeFileSync(seoRoutesPath, JSON.stringify(routes), 'utf8');

  const catalogueDataPath = join(root, 'catalogue-data.json');
  writeFileSync(catalogueDataPath, JSON.stringify({ workflows }), 'utf8');

  return { root, distDir, seoRoutesPath, catalogueDataPath };
}

test('throws when dist/index.html does not exist', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  const missingDist = join(distDir, '..', 'dist-missing');
  assert.throws(
    () => buildSeo({ distDir: missingDist, seoRoutesPath, catalogueDataPath }),
    /does not exist/,
  );
});

test('rewrites dist/index.html in place for the "/" route', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const html = readFileSync(join(distDir, 'index.html'), 'utf8');
  assert.match(html, /<title>Home Title<\/title>/);
  assert.match(html, /name="description" content="Home description\."/);
  assert.match(html, /rel="canonical" href="https:\/\/agentforge4j\.org\/"/);
});

test('writes a real static shell for every non-root route', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const html = readFileSync(join(distDir, 'architecture', 'index.html'), 'utf8');
  assert.match(html, /<title>Architecture — AgentForge4j<\/title>/);
  assert.match(html, /content="Architecture description\."/);
  assert.match(html, /href="https:\/\/agentforge4j\.org\/architecture\/"/);
});

test('a route with canonicalPath points its canonical/og:url at the target, not itself (trailing-slash form — the target\'s own served address)', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const html = readFileSync(join(distDir, 'contributing', 'index.html'), 'utf8');
  assert.match(html, /rel="canonical" href="https:\/\/agentforge4j\.org\/community\/"/);
  assert.match(html, /property="og:url" content="https:\/\/agentforge4j\.org\/community\/"/);
});

test('a route with sitemap: false is excluded from the sitemap fragment', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  const { sitemapUrls } = buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  assert.ok(!sitemapUrls.includes('https://agentforge4j.org/contributing/'));
});

test('every other route is included in the sitemap fragment exactly once, in the trailing-slash form GitHub Pages actually serves with no redirect', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  const { sitemapUrls } = buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  assert.deepEqual(
    [...sitemapUrls].sort(),
    ['https://agentforge4j.org/', 'https://agentforge4j.org/architecture/'].sort(),
  );
});

test('withTrailingSlash: the root path is unchanged; every other route gains exactly one trailing slash', () => {
  assert.equal(withTrailingSlash('/'), '/');
  assert.equal(withTrailingSlash('/api'), '/api/');
  assert.equal(withTrailingSlash('/catalogue/agent-creator'), '/catalogue/agent-creator/');
  assert.equal(withTrailingSlash('/already/'), '/already/');
});

test('writes a real dist/sitemap.xml with exactly the computed URLs, no duplicates', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.match(xml, /^<\?xml version="1\.0" encoding="UTF-8"\?>/);
  assert.match(xml, /<urlset xmlns="http:\/\/www\.sitemaps\.org\/schemas\/sitemap\/0\.9">/);
  const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  assert.deepEqual(new Set(locs).size, locs.length);
  for (const loc of locs) {
    assert.match(loc, /^https:\/\/agentforge4j\.org\//);
  }
});

test('generates a real static page + sitemap entry for every shipped catalogue workflow', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    workflows: [{ id: 'agent-creator', name: 'Agent Creator', description: 'Builds agents.' }],
  });
  const { sitemapUrls } = buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const html = readFileSync(join(distDir, 'catalogue', 'agent-creator', 'index.html'), 'utf8');
  assert.match(html, /<title>Agent Creator — AgentForge4j Catalogue<\/title>/);
  assert.match(html, /content="Builds agents\."/);
  assert.ok(sitemapUrls.includes('https://agentforge4j.org/catalogue/agent-creator/'));
});

test('falls back to a generic description when a workflow has none', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    workflows: [{ id: 'no-desc', name: 'No Description Workflow', description: null }],
  });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const html = readFileSync(join(distDir, 'catalogue', 'no-desc', 'index.html'), 'utf8');
  assert.match(html, /No Description Workflow — a shipped, ready-to-run AgentForge4j workflow/);
});

test('truncates an over-length workflow description rather than overflowing the meta tag', () => {
  const longDescription = 'A'.repeat(300);
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    workflows: [{ id: 'long', name: 'Long Workflow', description: longDescription }],
  });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const html = readFileSync(join(distDir, 'catalogue', 'long', 'index.html'), 'utf8');
  const match = /name="description" content="([^"]*)"/.exec(html);
  assert.ok(match);
  assert.ok(match[1].length <= 157);
  assert.ok(match[1].endsWith('…'));
});

test('fails closed on a duplicate computed sitemap URL', () => {
  const duplicateRoutes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home', description: 'Home.' },
      { path: '/dup', title: 'Dup 1', description: 'Dup 1.' },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    routes: duplicateRoutes,
    workflows: [{ id: 'dup', name: 'Dup workflow', description: 'x' }],
  });
  // Manufacture a real collision: the static route "/dup" and a catalogue workflow whose
  // computed path also lands on "/catalogue/dup" don't collide by construction — instead force
  // a collision the direct way, two static routes resolving to the exact same sitemap URL.
  writeFileSync(
    seoRoutesPath,
    JSON.stringify({
      siteUrl: 'https://agentforge4j.org',
      routes: [
        { path: '/', title: 'Home', description: 'Home.' },
        { path: '/dup', title: 'Dup 1', description: 'Dup 1.' },
        { path: '/dup', title: 'Dup 2', description: 'Dup 2.' },
      ],
    }),
  );
  assert.throws(() => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }), /duplicate URL/);
});

test('fails closed on a route path containing a ".." traversal segment', () => {
  const traversalRoutes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home', description: 'Home.' },
      { path: '/../../evil', title: 'Evil', description: 'Evil.' },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes: traversalRoutes });
  assert.throws(() => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }), /unsafe route path/);
});

test('injectHead cannot be broken out of the href/content attribute by an unescaped canonical value (adversarial: quotes, angle brackets, ampersand, script-like content)', () => {
  const maliciousCanonical = 'https://agentforge4j.org/catalogue/evil"><script>alert(1)</script>&x="';
  const html = injectHead(BASE_INDEX_HTML, {
    title: 'Evil Workflow — AgentForge4j Catalogue',
    description: 'desc',
    canonical: maliciousCanonical,
  });
  // No live <script> element may appear anywhere in the generated shell.
  assert.ok(!/<script>/.test(html), 'a live <script> element must never appear in the generated shell');
  // Every canonical-bearing tag must remain a single, well-formed element whose attribute value
  // is the fully-escaped string, quote-for-quote — i.e. it is structurally impossible to break out
  // of the href/content attribute using the malicious input above.
  const escaped = escapeHtml(maliciousCanonical);
  assert.match(html, new RegExp(`<link rel="canonical" href="${escaped.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}" />`));
  assert.match(html, new RegExp(`<meta property="og:url" content="${escaped.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}" />`));
});

test('fails closed on a catalogue workflow id outside the required slug contract', () => {
  const invalidIds = [
    '',
    '.',
    '..',
    '/etc/passwd',
    'catalogue\\evil',
    'Agent-Creator', // uppercase
    '-agent-creator', // leading hyphen
    'agent-creator-', // trailing hyphen
    'agent--creator', // duplicate hyphen
    'tom & jerry', // space, ampersand
    'evil"><script>', // HTML metacharacters
  ];
  for (const id of invalidIds) {
    const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
      workflows: [{ id, name: 'Bad Workflow', description: 'x' }],
    });
    assert.throws(
      () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
      /unsafe catalogue workflow id/,
      `expected id ${JSON.stringify(id)} to be rejected`,
    );
  }
});

test('fails closed on a non-string catalogue workflow id, even when RegExp.test\'s implicit String() coercion would otherwise accept it', () => {
  // RegExp.prototype.test coerces its argument via String(...): String(123) === "123" and
  // String(null) === "null" both satisfy the slug pattern's character class on their own, so the
  // typeof guard — not the pattern alone — is what must reject these.
  const malformedWorkflows = [
    { name: 'Missing Id', description: 'x' }, // `id` key omitted entirely -> workflow.id is undefined
    { id: null, name: 'Null Id', description: 'x' },
    { id: 123, name: 'Number Id', description: 'x' },
    { id: true, name: 'Boolean Id', description: 'x' },
    { id: ['a', 'b'], name: 'Array Id', description: 'x' },
    { id: {}, name: 'Object Id', description: 'x' },
    { id: '', name: 'Empty String Id', description: 'x' },
  ];
  for (const workflow of malformedWorkflows) {
    const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ workflows: [workflow] });
    assert.throws(
      () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
      /unsafe catalogue workflow id/,
      `expected workflow ${JSON.stringify(workflow)} to be rejected`,
    );
  }
});

test('a "." workflow id is rejected rather than silently overwriting the catalogue index shell', () => {
  // Unchecked, path.join collapses a "." segment away: join(distDir, 'catalogue', '.') ===
  // join(distDir, 'catalogue') — a "." id would write its shell over /catalogue's own index.html
  // instead of getting a distinct page. Both defenses (the id contract and assertSafeRoutePath's
  // own "." rejection) must independently refuse this before any write happens.
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    workflows: [{ id: '.', name: 'Dot Workflow', description: 'x' }],
  });
  assert.throws(() => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }));
  assert.ok(
    !existsSync(join(distDir, 'catalogue', 'index.html')),
    'the catalogue list page shell must not exist yet — buildSeo must fail before writing anything for the bad id',
  );
});

test('every currently shipped real catalogue workflow id satisfies the slug contract', () => {
  const { workflows } = JSON.parse(readFileSync(join(REAL_MODULE_ROOT, 'src/generated/catalogue-data.json'), 'utf8'));
  assert.ok(workflows.length > 0, 'expected at least one real shipped workflow to check');
  for (const workflow of workflows) {
    assert.match(workflow.id, WORKFLOW_ID_PATTERN, `real shipped id ${JSON.stringify(workflow.id)} must satisfy the contract`);
  }
});

test('the committed index.html home meta matches seo-routes.json\'s "/" entry (build-seo overwrites it at build time; this guards against the two silently drifting for local dev/preview)', () => {
  const html = readFileSync(join(REAL_MODULE_ROOT, 'index.html'), 'utf8');
  const { routes } = JSON.parse(readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'));
  const home = routes.find((route) => route.path === '/');
  assert.ok(home, 'seo-routes.json must define a "/" entry');
  assert.match(html, new RegExp(`<title>${home.title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}</title>`));
  assert.ok(html.includes(home.description), 'index.html description must match the "/" entry verbatim');
});

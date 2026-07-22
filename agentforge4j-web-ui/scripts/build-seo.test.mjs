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
import {
  buildSeo,
  escapeHtml,
  gitLastModifiedDate,
  injectHead,
  injectJsonLd,
  injectRoot,
  newestGitLastModifiedDate,
  withTrailingSlash,
} from './build-seo.mjs';
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

test('every route declared in the real committed seo-routes.json has a sourceFiles entry that resolves to a real file (a silent typo/rename here would quietly drop that route\'s <lastmod> with no build failure)', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const { sharedSourceFiles, routes } = JSON.parse(
    readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'),
  );
  assert.ok(routes.length > 0, 'expected at least one real route to check');
  for (const route of routes) {
    assert.ok(
      Array.isArray(route.sourceFiles) && route.sourceFiles.length > 0,
      `route "${route.path}" must declare a non-empty sourceFiles array`,
    );
    for (const relFile of route.sourceFiles) {
      assert.ok(
        existsSync(join(repoRoot, relFile)),
        `route "${route.path}" declares sourceFiles entry "${relFile}", which does not exist at ` +
          `${join(repoRoot, relFile)} — this route would silently ship with a stale <lastmod>`,
      );
    }
  }
  assert.ok(Array.isArray(sharedSourceFiles) && sharedSourceFiles.length > 0, 'expected a non-empty sharedSourceFiles list');
  for (const relFile of sharedSourceFiles) {
    assert.ok(
      existsSync(join(repoRoot, relFile)),
      `sharedSourceFiles entry "${relFile}" does not exist at ${join(repoRoot, relFile)} — every route's <lastmod> ` +
        'would silently ignore changes to it',
    );
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

test('withTrailingSlash: the root path is unchanged; every other route gains exactly one trailing slash', () => {
  assert.equal(withTrailingSlash('/'), '/');
  assert.equal(withTrailingSlash('/api'), '/api/');
  assert.equal(withTrailingSlash('/catalogue/agent-creator'), '/catalogue/agent-creator/');
  assert.equal(withTrailingSlash('/already/'), '/already/');
});

test('injectRoot splices real prerendered markup into the empty mount point', () => {
  const html = injectRoot(BASE_INDEX_HTML, '<header>Real Nav</header><main><h1>Real Title</h1></main>');
  assert.match(html, /<div id="root"><header>Real Nav<\/header><main><h1>Real Title<\/h1><\/main><\/div>/);
});

test('injectRoot is a no-op when no snapshot was captured for a route (fixture tests with no headless browser)', () => {
  assert.equal(injectRoot(BASE_INDEX_HTML, undefined), BASE_INDEX_HTML);
});

test('injectRoot fails closed when the shell has no empty mount point to splice into (template drift)', () => {
  const alreadyFilled = BASE_INDEX_HTML.replace('<div id="root"></div>', '<div id="root">already has content</div>');
  assert.throws(() => injectRoot(alreadyFilled, '<h1>x</h1>'), /empty <div id="root">/);
});

test('injectJsonLd inserts a valid, parseable JSON-LD script before </head> when a route declares one', () => {
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  const html = injectJsonLd(BASE_INDEX_HTML, jsonLd);
  const match = /<script type="application\/ld\+json">([\s\S]*?)<\/script>\s*<\/head>/.exec(html);
  assert.ok(match, 'expected a JSON-LD script immediately before </head>');
  assert.deepEqual(JSON.parse(match[1]), jsonLd);
});

test('injectJsonLd is a no-op for routes that declare no jsonLd (every route except "/")', () => {
  assert.equal(injectJsonLd(BASE_INDEX_HTML, undefined), BASE_INDEX_HTML);
});

test('injectJsonLd cannot be broken out of the <script> body by a value containing a literal </script> sequence', () => {
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', description: 'a</script><script>alert(1)</script>' };
  const html = injectJsonLd(BASE_INDEX_HTML, jsonLd);
  // The only real <script> tag in the document must be the one this function itself wrote — none
  // injected via the JSON-LD payload's own string content.
  const scriptOpenTags = html.match(/<script[ >]/g) ?? [];
  assert.equal(scriptOpenTags.length, 1, 'expected exactly the one JSON-LD script — no extra <script> from the payload');
  // The literal, unescaped sequence must never appear in the emitted HTML at all.
  assert.ok(!html.includes('</script><script>alert(1)</script>'), 'the raw </script> sequence from the JSON-LD value must not reach the HTML unescaped');
  // Semantics are unchanged: parsing the actual emitted JSON-LD block back out still yields the
  // exact original string, </script> and all — this is an encoding fix, not a content change.
  const match = /<script type="application\/ld\+json">([\s\S]*?)<\/script>\s*<\/head>/.exec(html);
  assert.ok(match, 'expected a JSON-LD script immediately before </head>');
  assert.deepEqual(JSON.parse(match[1]), jsonLd);
});

test('buildSeo splices the "/" route\'s jsonLd (seo-routes.json) into the real built index.html shell, and no other route gets one', () => {
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'Home' };
  const routesWithJsonLd = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home', description: 'Home.', jsonLd },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.' },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes: routesWithJsonLd });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath });
  const homeHtml = readFileSync(join(distDir, 'index.html'), 'utf8');
  const archHtml = readFileSync(join(distDir, 'architecture', 'index.html'), 'utf8');
  assert.match(homeHtml, /application\/ld\+json/);
  assert.doesNotMatch(archHtml, /application\/ld\+json/);
});

test('buildSeo splices a route\'s prerendered snapshot into its own shell only, leaving routes with no captured snapshot untouched', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  buildSeo({
    distDir,
    seoRoutesPath,
    catalogueDataPath,
    snapshots: { '/': '<h1>Real Home Content</h1>' },
  });
  const homeHtml = readFileSync(join(distDir, 'index.html'), 'utf8');
  const archHtml = readFileSync(join(distDir, 'architecture', 'index.html'), 'utf8');
  assert.match(homeHtml, /<div id="root"><h1>Real Home Content<\/h1><\/div>/);
  assert.match(archHtml, /<div id="root"><\/div>/, 'a route with no captured snapshot keeps its empty mount point, not a stale/wrong one');
});

test('gitLastModifiedDate returns a real, valid W3C date (YYYY-MM-DD) for a real committed file, and null when no source file is declared', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const date = gitLastModifiedDate(repoRoot, 'agentforge4j-web-ui/package.json');
  assert.match(date, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(gitLastModifiedDate(repoRoot, undefined), null);
});

test('a route with a sourceFiles entry gets a real git-derived <lastmod>; a route without one (and no sharedSourceFiles declared) gets none (never an invented date)', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const routesWithSourceFile = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home', description: 'Home.', sourceFiles: ['agentforge4j-web-ui/package.json'] },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.' },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes: routesWithSourceFile });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  const homeBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/<\/loc>[\s\S]*?<\/url>/.exec(xml)[0];
  const archBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/architecture\/<\/loc>[\s\S]*?<\/url>/.exec(xml)[0];
  assert.match(homeBlock, /<lastmod>\d{4}-\d{2}-\d{2}<\/lastmod>/);
  assert.doesNotMatch(archBlock, /<lastmod>/);
});

test('newestGitLastModifiedDate returns the newest (most recent) real git-derived date across several files, order-independent, and null only when every entry is null', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const fileA = 'agentforge4j-web-ui/package.json';
  const fileB = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const dateA = gitLastModifiedDate(repoRoot, fileA);
  const dateB = gitLastModifiedDate(repoRoot, fileB);
  const expectedNewest = dateA > dateB ? dateA : dateB;

  assert.equal(newestGitLastModifiedDate(repoRoot, [fileA, fileB]), expectedNewest);
  assert.equal(newestGitLastModifiedDate(repoRoot, [fileB, fileA]), expectedNewest, 'must not depend on array order');
  assert.equal(newestGitLastModifiedDate(repoRoot, [fileA, undefined, null]), dateA, 'null/undefined entries are ignored, not treated as "newest"');
  assert.equal(newestGitLastModifiedDate(repoRoot, []), null);
  assert.equal(newestGitLastModifiedDate(repoRoot, undefined), null);
});

test('newest-wins: a route declaring two real sourceFiles gets the newer of the two as its <lastmod>, regardless of declaration order', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const fileA = 'agentforge4j-web-ui/package.json';
  const fileB = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const expectedNewest = newestGitLastModifiedDate(repoRoot, [fileA, fileB]);

  for (const sourceFiles of [[fileA, fileB], [fileB, fileA]]) {
    const routes = {
      siteUrl: 'https://agentforge4j.org',
      routes: [{ path: '/', title: 'Home', description: 'Home.', sourceFiles }],
    };
    const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
    buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
    const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
    const homeBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/<\/loc>[\s\S]*?<\/url>/.exec(xml)[0];
    assert.match(homeBlock, new RegExp(`<lastmod>${expectedNewest}</lastmod>`));
  }
});

test('shared dependency: a top-level sharedSourceFiles entry contributes to every route\'s <lastmod>, even a route whose own sourceFiles alone would resolve to an older date', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const ownFile = 'agentforge4j-web-ui/package.json';
  const sharedFile = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const expectedNewest = newestGitLastModifiedDate(repoRoot, [ownFile, sharedFile]);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    sharedSourceFiles: [sharedFile],
    routes: [{ path: '/', title: 'Home', description: 'Home.', sourceFiles: [ownFile] }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  const homeBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/<\/loc>[\s\S]*?<\/url>/.exec(xml)[0];
  assert.match(homeBlock, new RegExp(`<lastmod>${expectedNewest}</lastmod>`));
});

test('metadata-only change: a route with no sourceFiles of its own still gets a real <lastmod> purely from a sharedSourceFiles entry (proves a route-metadata-only edit — e.g. to seo-routes.json itself — is never silently invisible to lastmod)', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const sharedFile = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const expectedDate = gitLastModifiedDate(repoRoot, sharedFile);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    sharedSourceFiles: [sharedFile],
    routes: [{ path: '/', title: 'Home', description: 'Home.' }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  const homeBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/<\/loc>[\s\S]*?<\/url>/.exec(xml)[0];
  assert.match(homeBlock, new RegExp(`<lastmod>${expectedDate}</lastmod>`));
});

test('isolation: sharedSourceFiles/per-route sourceFiles apply only to the declaring seo-routes.json — a route in one fixture is never affected by another fixture\'s (or another route\'s) unrelated sourceFiles', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const fileA = 'agentforge4j-web-ui/package.json';
  const fileB = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const dateA = gitLastModifiedDate(repoRoot, fileA);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home', description: 'Home.', sourceFiles: [fileA] },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.', sourceFiles: [fileB] },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  const homeBlock = /<url>\s*<loc>https:\/\/agentforge4j\.org\/<\/loc>[\s\S]*?<\/url>/.exec(xml)[0];
  assert.match(
    homeBlock,
    new RegExp(`<lastmod>${dateA}</lastmod>`),
    '"/" must reflect only its own declared sourceFiles (fileA), unaffected by "/architecture"\'s unrelated fileB',
  );
});

// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for the per-route SEO shell + sitemap-fragment generator. Fixture dist/,
// seo-routes.json, and catalogue-data.json under a temp root stand in for a real build — no
// `vite build` required.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  buildSeo,
  escapeHtml,
  gitLastModifiedDate,
  gitLastModifiedDateForRouteMetadata,
  injectHead,
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

/** A dist/ + empty catalogue-data.json pair with no seo-routes.json of its own — for tests that
 * need to control exactly where seo-routes.json lives (a real, disposable git repo, so
 * gitLastModifiedDateForRouteMetadata's `git log -L` history lookup has something real to find). */
function distFixture() {
  const root = mkdtempSync(join(tmpdir(), 'build-seo-dist-'));
  const distDir = join(root, 'dist');
  mkdirSync(distDir, { recursive: true });
  writeFileSync(join(distDir, 'index.html'), BASE_INDEX_HTML, 'utf8');
  const catalogueDataPath = join(root, 'catalogue-data.json');
  writeFileSync(catalogueDataPath, JSON.stringify({ workflows: [] }), 'utf8');
  return { distDir, catalogueDataPath };
}

/** A fresh, disposable, real git repository — genuinely committed history, distinct from this
 * checkout's own, so per-route metadata-block isolation (gitLastModifiedDateForRouteMetadata) can
 * be proven with controlled, backdated commits instead of depending on this repo's own ambient
 * history (which changes over time and would make the test non-deterministic). */
function initTempGitRepo() {
  const root = mkdtempSync(join(tmpdir(), 'build-seo-git-'));
  execFileSync('git', ['init', '-q'], { cwd: root });
  execFileSync('git', ['config', 'user.email', 'test@example.com'], { cwd: root });
  execFileSync('git', ['config', 'user.name', 'Test'], { cwd: root });
  return root;
}

/** Writes (or, for a `null` value, deletes) each `relPath -> content` entry inside `repoRoot` and
 * commits the whole set together with an explicit, fixed author/committer date
 * (`YYYY-MM-DDTHH:MM:SS`) — controlling exactly what `git log`/`git log -L` report, rather than
 * relying on "now" (unusable here — same-day commits are indistinguishable at the `%cs` date-only
 * resolution this codebase deliberately uses for readability). `git add -A` picks up a deletion
 * the same way it picks up a write, so removing a shipped workflow is exercised the same real way
 * a genuine removal would happen (delete the file, commit), not simulated any other way. */
function writeFilesAndCommit(repoRoot, files, isoDateTime) {
  for (const [relPath, content] of Object.entries(files)) {
    const fullPath = join(repoRoot, relPath);
    if (content === null) {
      rmSync(fullPath, { recursive: true, force: true });
      continue;
    }
    mkdirSync(dirname(fullPath), { recursive: true });
    writeFileSync(fullPath, content, 'utf8');
  }
  const env = { ...process.env, GIT_AUTHOR_DATE: isoDateTime, GIT_COMMITTER_DATE: isoDateTime };
  const stdio = ['ignore', 'ignore', 'ignore']; // silence git's autocrlf/LF-will-be-replaced noise
  execFileSync('git', ['add', '-A'], { cwd: repoRoot, env, stdio });
  execFileSync('git', ['commit', '-q', '-m', 'update'], { cwd: repoRoot, env, stdio });
}

/** Writes seo-routes.json at `relPath` inside `repoRoot` and commits it alone — a thin
 * single-file wrapper over `writeFilesAndCommit` for the route-metadata-isolation tests below. */
function commitSeoRoutesAt(repoRoot, relPath, content, isoDateTime) {
  writeFilesAndCommit(repoRoot, { [relPath]: JSON.stringify(content, null, 2) }, isoDateTime);
}

const SHIPPED_WORKFLOWS_INDEX_REL_PATH = 'agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/index';
const AGGREGATE_GENERATOR_REL_PATH = 'agentforge4j-web-ui/scripts/build-catalogue-data.mjs';
const AGGREGATE_ADAPTER_REL_PATH = 'agentforge4j-web-ui/src/lib/catalogueData.ts';
const AGGREGATE_COPY_REL_PATH = 'agentforge4j-web-ui/src/copy/catalogue.ts';
const SEO_ROUTES_REL_PATH = 'agentforge4j-web-ui/src/config/seo-routes.json';

function shippedWorkflowRelPath(id) {
  return `agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/${id}.workflow/workflow.json`;
}

/** A disposable git repo pre-populated with everything the aggregate `/catalogue/` mechanism
 * reads: a seo-routes.json declaring `aggregateCatalogueSourceFiles` and a `/catalogue` route
 * flagged `aggregatesCatalogueWorkflows: true` (plus an unrelated `/architecture` route, for the
 * "catalogue-only changes don't leak" tests), stand-in generator/adapter/copy files, the shipped-
 * workflows `index`, and one `<id>.workflow/workflow.json` per id in `workflowIds` — all in one
 * initial commit at `isoDateTime`. */
function setupCatalogueAggregateRepo(workflowIds, isoDateTime) {
  const repoRoot = initTempGitRepo();
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    aggregateCatalogueSourceFiles: [AGGREGATE_GENERATOR_REL_PATH, AGGREGATE_ADAPTER_REL_PATH],
    // Mirrors the real production seo-routes.json: build-catalogue-data.mjs and catalogueData.ts
    // are genuine dependencies of every catalogue *detail* page too (they render through the same
    // generator/adapter), not just the aggregate list.
    catalogueSourceFiles: [AGGREGATE_GENERATOR_REL_PATH, AGGREGATE_ADAPTER_REL_PATH],
    routes: [
      {
        path: '/catalogue',
        title: 'Catalogue',
        description: 'Catalogue.',
        sourceFiles: [AGGREGATE_COPY_REL_PATH],
        aggregatesCatalogueWorkflows: true,
      },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.' },
    ],
  };
  const files = {
    [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2),
    [AGGREGATE_GENERATOR_REL_PATH]: '// generator v1\n',
    [AGGREGATE_ADAPTER_REL_PATH]: '// adapter v1\n',
    [AGGREGATE_COPY_REL_PATH]: '// copy v1\n',
    [SHIPPED_WORKFLOWS_INDEX_REL_PATH]: `${workflowIds.join('\n')}\n`,
  };
  for (const id of workflowIds) {
    files[shippedWorkflowRelPath(id)] = JSON.stringify({ id, name: `${id} name v1`, description: `${id} description v1` });
  }
  writeFilesAndCommit(repoRoot, files, isoDateTime);
  return { repoRoot, seoRoutesPath: join(repoRoot, SEO_ROUTES_REL_PATH) };
}

/** Extracts the `<lastmod>` (or `null`) for one `<url>` block matching `url` exactly, out of a
 * generated sitemap.xml. */
function lastmodFor(xml, url) {
  const escapedUrl = url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const blockMatch = new RegExp(`<url>\\s*<loc>${escapedUrl}</loc>[\\s\\S]*?</url>`).exec(xml);
  assert.ok(blockMatch, `expected a <url> block for ${url} in the generated sitemap.xml`);
  const lastmodMatch = /<lastmod>([^<]+)<\/lastmod>/.exec(blockMatch[0]);
  return lastmodMatch ? lastmodMatch[1] : null;
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

test('every route declared in the real committed seo-routes.json has a sourceFiles entry that resolves to a real file, and every artifactGenerationSourceFiles/globalSourceFiles/catalogueSourceFiles/aggregateCatalogueSourceFiles entry does too (a silent typo/rename here would quietly drop that route\'s <lastmod> with no build failure)', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const { artifactGenerationSourceFiles, globalSourceFiles, catalogueSourceFiles, aggregateCatalogueSourceFiles, routes } = JSON.parse(
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
  const namedLists = [
    ['artifactGenerationSourceFiles', artifactGenerationSourceFiles],
    ['globalSourceFiles', globalSourceFiles],
    ['catalogueSourceFiles', catalogueSourceFiles],
    ['aggregateCatalogueSourceFiles', aggregateCatalogueSourceFiles],
  ];
  for (const [key, list] of namedLists) {
    assert.ok(Array.isArray(list) && list.length > 0, `expected a non-empty ${key} list`);
    for (const relFile of list) {
      assert.ok(
        existsSync(join(repoRoot, relFile)),
        `${key} entry "${relFile}" does not exist at ${join(repoRoot, relFile)} — every affected page's <lastmod> ` +
          'would silently ignore changes to it',
      );
    }
  }
  const catalogueRoute = routes.find((route) => route.path === '/catalogue');
  assert.ok(catalogueRoute, 'expected a "/catalogue" route');
  assert.equal(
    catalogueRoute.aggregatesCatalogueWorkflows,
    true,
    '"/catalogue" must be flagged aggregatesCatalogueWorkflows so its <lastmod> reflects every shipped workflow',
  );
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

test('a route with a sourceFiles entry gets a real git-derived <lastmod>; a route with neither sourceFiles nor a matching metadata block gets none (never an invented date)', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const routesWithSourceFile = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/test-has-sourcefile', title: 'Has One', description: 'Has one.', sourceFiles: ['agentforge4j-web-ui/package.json'] },
      { path: '/test-has-nothing', title: 'Has Nothing', description: 'Has nothing.' },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes: routesWithSourceFile });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.match(lastmodFor(xml, 'https://agentforge4j.org/test-has-sourcefile/'), /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/test-has-nothing/'), null);
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
      routes: [{ path: '/test-newest-wins', title: 'T', description: 'T.', sourceFiles }],
    };
    const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
    buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
    const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
    assert.equal(lastmodFor(xml, 'https://agentforge4j.org/test-newest-wins/'), expectedNewest);
  }
});

test('global dependency: a top-level globalSourceFiles entry contributes to a static route\'s <lastmod>, even when its own sourceFiles alone would resolve to an older date', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const ownFile = 'agentforge4j-web-ui/package.json';
  const globalFile = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const expectedNewest = newestGitLastModifiedDate(repoRoot, [ownFile, globalFile]);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    globalSourceFiles: [globalFile],
    routes: [{ path: '/test-global-dep', title: 'T', description: 'T.', sourceFiles: [ownFile] }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/test-global-dep/'), expectedNewest);
});

test('isolation (own sourceFiles): a static route\'s <lastmod> reflects only its own declared sourceFiles, unaffected by another route\'s unrelated sourceFiles', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const fileA = 'agentforge4j-web-ui/package.json';
  const fileB = 'agentforge4j-web-ui/src/config/seo-routes.json';
  const dateA = gitLastModifiedDate(repoRoot, fileA);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/test-isolation-a', title: 'A', description: 'A.', sourceFiles: [fileA] },
      { path: '/test-isolation-b', title: 'B', description: 'B.', sourceFiles: [fileB] },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(
    lastmodFor(xml, 'https://agentforge4j.org/test-isolation-a/'),
    dateA,
    '"/test-isolation-a" must reflect only its own declared sourceFiles (fileA), unaffected by "/test-isolation-b"\'s unrelated fileB',
  );
});

test('gitLastModifiedDateForRouteMetadata returns the newest commit that touched a real route\'s own JSON block, and null for a route path not present in the file', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const seoRoutesPath = join(REAL_MODULE_ROOT, 'src/config/seo-routes.json');
  const date = gitLastModifiedDateForRouteMetadata(repoRoot, seoRoutesPath, '/architecture');
  assert.match(date, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(gitLastModifiedDateForRouteMetadata(repoRoot, seoRoutesPath, '/this-route-does-not-exist'), null);
});

test('gitLastModifiedDateForRouteMetadata returns null when seoRoutesPath resolves outside repoRoot (a fixture seo-routes.json unrelated to that repository\'s own git history)', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const outsidePath = join(tmpdir(), 'unrelated-seo-routes.json');
  assert.equal(gitLastModifiedDateForRouteMetadata(repoRoot, outsidePath, '/'), null);
});

test('homepage metadata isolation: changing only "/" own metadata (title/description) bumps only "/" — "/architecture" retains its previous <lastmod>', () => {
  const repoRoot = initTempGitRepo();
  const relSeoRoutes = 'seo-routes.json';
  const seoRoutesPath = join(repoRoot, relSeoRoutes);
  const { distDir, catalogueDataPath } = distFixture();

  const routesV1 = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home V1', description: 'Home V1 description.' },
      { path: '/architecture', title: 'Architecture V1', description: 'Architecture V1 description.' },
    ],
  };
  commitSeoRoutesAt(repoRoot, relSeoRoutes, routesV1, '2020-01-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xmlBefore = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xmlBefore, 'https://agentforge4j.org/'), '2020-01-01');
  assert.equal(lastmodFor(xmlBefore, 'https://agentforge4j.org/architecture/'), '2020-01-01');

  const routesV2 = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home V2 — changed', description: 'Home V1 description.' },
      { path: '/architecture', title: 'Architecture V1', description: 'Architecture V1 description.' },
    ],
  };
  commitSeoRoutesAt(repoRoot, relSeoRoutes, routesV2, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xmlAfter = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(
    lastmodFor(xmlAfter, 'https://agentforge4j.org/'),
    '2020-06-01',
    '"/" own metadata changed — its <lastmod> must bump to the new commit date',
  );
  assert.equal(
    lastmodFor(xmlAfter, 'https://agentforge4j.org/architecture/'),
    '2020-01-01',
    '"/architecture" own metadata was never touched by that commit — its <lastmod> must be unaffected',
  );
});

test('static-route isolation: changing metadata for one static page does not update an unrelated static page (distinct from the homepage case above — proves this is a general rule, not special-cased to "/")', () => {
  const repoRoot = initTempGitRepo();
  const relSeoRoutes = 'seo-routes.json';
  const seoRoutesPath = join(repoRoot, relSeoRoutes);
  const { distDir, catalogueDataPath } = distFixture();

  const routesV1 = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/security', title: 'Security V1', description: 'Security V1 description.' },
      { path: '/legal', title: 'Legal V1', description: 'Legal V1 description.' },
    ],
  };
  commitSeoRoutesAt(repoRoot, relSeoRoutes, routesV1, '2020-01-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });

  const routesV2 = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/security', title: 'Security V2 — changed', description: 'Security V1 description.' },
      { path: '/legal', title: 'Legal V1', description: 'Legal V1 description.' },
    ],
  };
  commitSeoRoutesAt(repoRoot, relSeoRoutes, routesV2, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/security/'), '2020-06-01');
  assert.equal(
    lastmodFor(xml, 'https://agentforge4j.org/legal/'),
    '2020-01-01',
    '"/legal" own metadata was never touched — its <lastmod> must be unaffected by "/security"\'s edit',
  );
});

test('catalogue isolation: a static route\'s own sourceFiles/metadata never influence a catalogue workflow\'s <lastmod>', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const workflows = [{ id: 'agent-creator', name: 'Agent Creator', description: 'Builds agents.' }];
  const routesA = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/test-static-a', title: 'A', description: 'A.', sourceFiles: ['agentforge4j-web-ui/package.json'] }],
  };
  const routesB = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      {
        path: '/test-static-a',
        title: 'Completely different title',
        description: 'Completely different description.',
        sourceFiles: ['agentforge4j-web-ui/src/config/seo-routes.json'],
      },
    ],
  };
  const fixtureA = fixture({ routes: routesA, workflows });
  const fixtureB = fixture({ routes: routesB, workflows });
  buildSeo({ distDir: fixtureA.distDir, seoRoutesPath: fixtureA.seoRoutesPath, catalogueDataPath: fixtureA.catalogueDataPath, repoRoot });
  buildSeo({ distDir: fixtureB.distDir, seoRoutesPath: fixtureB.seoRoutesPath, catalogueDataPath: fixtureB.catalogueDataPath, repoRoot });
  const xmlA = readFileSync(join(fixtureA.distDir, 'sitemap.xml'), 'utf8');
  const xmlB = readFileSync(join(fixtureB.distDir, 'sitemap.xml'), 'utf8');
  const workflowUrl = 'https://agentforge4j.org/catalogue/agent-creator/';
  assert.equal(
    lastmodFor(xmlA, workflowUrl),
    lastmodFor(xmlB, workflowUrl),
    'the catalogue workflow\'s <lastmod> must be identical regardless of an unrelated static route\'s sourceFiles/metadata',
  );
});

test('catalogue renderer: a real catalogueSourceFiles entry (shared by every catalogue detail page) contributes to every workflow\'s <lastmod>', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const catalogueSourceFile = 'agentforge4j-web-ui/src/pages/CatalogueDetailPage.tsx';
  const workflowFile = 'agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/agent-creator.workflow/workflow.json';
  const expectedNewest = newestGitLastModifiedDate(repoRoot, [catalogueSourceFile, workflowFile]);

  const routes = { siteUrl: 'https://agentforge4j.org', catalogueSourceFiles: [catalogueSourceFile], routes: [] };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    routes,
    workflows: [{ id: 'agent-creator', name: 'Agent Creator', description: 'Builds agents.' }],
  });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/catalogue/agent-creator/'), expectedNewest);
});

test('workflow isolation: each catalogue workflow\'s <lastmod> reflects only its own workflow.json, never another workflow\'s file', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const idA = 'agent-creator';
  const idB = 'workflow-execution-estimator';
  const fileA = `agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/${idA}.workflow/workflow.json`;
  const dateA = gitLastModifiedDate(repoRoot, fileA);

  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    routes: { siteUrl: 'https://agentforge4j.org', routes: [] },
    workflows: [
      { id: idA, name: 'Agent Creator', description: 'x' },
      { id: idB, name: 'Workflow Execution Estimator', description: 'y' },
    ],
  });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(
    lastmodFor(xml, `https://agentforge4j.org/catalogue/${idA}/`),
    dateA,
    `workflow "${idA}"'s <lastmod> must equal only its own file's date, unaffected by "${idB}"'s unrelated file`,
  );
});

test('shared shell: a real globalSourceFiles entry contributes identically to both a static route and a catalogue workflow\'s <lastmod>', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const globalFile = 'agentforge4j-web-ui/src/App.tsx';
  const staticOwnFile = 'agentforge4j-web-ui/package.json';
  const workflowFile = 'agentforge4j-workflows-catalog/src/main/resources/shipped-workflows/agent-creator.workflow/workflow.json';
  const expectedStatic = newestGitLastModifiedDate(repoRoot, [globalFile, staticOwnFile]);
  const expectedWorkflow = newestGitLastModifiedDate(repoRoot, [globalFile, workflowFile]);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    globalSourceFiles: [globalFile],
    routes: [{ path: '/test-shared-shell', title: 'T', description: 'T.', sourceFiles: [staticOwnFile] }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    routes,
    workflows: [{ id: 'agent-creator', name: 'Agent Creator', description: 'x' }],
  });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/test-shared-shell/'), expectedStatic);
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/catalogue/agent-creator/'), expectedWorkflow);
});

// --- Aggregate /catalogue/ dependency model ------------------------------------------------
//
// /catalogue/ (CataloguePage.tsx) renders the *aggregate* workflow list — every shipped
// workflow's name/description, in index order — so its own <lastmod> must reflect the newest of:
// aggregateCatalogueSourceFiles (projection/adapter logic), the shipped-workflows `index` file
// itself (addition/removal/reordering), and every currently-indexed workflow's own workflow.json
// (name/description). All tests below use a real, disposable git repo with controlled backdated
// commits — none of this is achievable against this checkout's own ambient history, since
// same-day commits are indistinguishable at %cs's date-only resolution.

test('workflow name change updates /catalogue/\'s <lastmod>', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a', 'wf-b'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'), '2020-01-01');

  writeFilesAndCommit(
    repoRoot,
    { [shippedWorkflowRelPath('wf-a')]: JSON.stringify({ id: 'wf-a', name: 'Renamed Workflow A', description: 'wf-a description v1' }) },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(
    lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'),
    '2020-06-01',
    '/catalogue/ renders every workflow\'s name, so a name-only change to one of them must bump its <lastmod>',
  );
});

test('workflow description change updates /catalogue/\'s <lastmod>', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a', 'wf-b'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'), '2020-01-01');

  writeFilesAndCommit(
    repoRoot,
    { [shippedWorkflowRelPath('wf-b')]: JSON.stringify({ id: 'wf-b', name: 'wf-b name v1', description: 'Completely rewritten description' }) },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(
    lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'),
    '2020-06-01',
    '/catalogue/ renders every workflow\'s description, so a description-only change must bump its <lastmod>',
  );
});

test('adding a shipped workflow updates /catalogue/\'s <lastmod> — and a newly discovered workflow contributes automatically, with no second workflow-id list to maintain', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'), '2020-01-01');

  // Only the index and the new workflow's own file are touched — seo-routes.json (and its
  // aggregateCatalogueSourceFiles list) is never edited, proving the new id participates purely
  // by virtue of being read fresh from `index` on the next build.
  writeFilesAndCommit(
    repoRoot,
    {
      [SHIPPED_WORKFLOWS_INDEX_REL_PATH]: 'wf-a\nwf-c\n',
      [shippedWorkflowRelPath('wf-c')]: JSON.stringify({ id: 'wf-c', name: 'wf-c name v1', description: 'wf-c description v1' }),
    },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(
    lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'),
    '2020-06-01',
    'adding a workflow to the index must bump /catalogue/\'s <lastmod>',
  );
});

test('removing a shipped workflow updates /catalogue/\'s <lastmod> (deleting its bundle and editing the index out are themselves real, tracked changes)', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a', 'wf-b'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'), '2020-01-01');

  writeFilesAndCommit(
    repoRoot,
    {
      [SHIPPED_WORKFLOWS_INDEX_REL_PATH]: 'wf-a\n',
      [shippedWorkflowRelPath('wf-b')]: null,
    },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(
    lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'),
    '2020-06-01',
    'removing a workflow from the index must bump /catalogue/\'s <lastmod>',
  );
});

/** A catalogueDataPath fixture carrying one real workflow entry per id — for tests that need
 * real catalogue *detail* pages to actually get generated (setupCatalogueAggregateRepo alone only
 * wires up the aggregate list; its own catalogueDataPath default is empty). */
function catalogueDataFixtureFor(workflowIds) {
  const { distDir } = distFixture();
  const catalogueDataPath = join(dirname(distDir), 'catalogue-data.json');
  writeFileSync(
    catalogueDataPath,
    JSON.stringify({ workflows: workflowIds.map((id) => ({ id, name: `${id} name v1`, description: `${id} description v1` })) }),
    'utf8',
  );
  return { distDir, catalogueDataPath };
}

test('a catalogue projection/generation logic change (build-catalogue-data.mjs) updates /catalogue/\'s <lastmod> AND every catalogue detail page\'s <lastmod> (it is a real rendering dependency of both)', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a', 'wf-b'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = catalogueDataFixtureFor(['wf-a', 'wf-b']);
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/catalogue/'), '2020-01-01');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/catalogue/wf-a/'), '2020-01-01');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/catalogue/wf-b/'), '2020-01-01');

  writeFilesAndCommit(repoRoot, { [AGGREGATE_GENERATOR_REL_PATH]: '// generator v2 — changed projection logic\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/catalogue/'), '2020-06-01', 'the aggregate list must bump');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/catalogue/wf-a/'), '2020-06-01', 'every detail page rendered through the generator must bump');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/catalogue/wf-b/'), '2020-06-01', 'every detail page rendered through the generator must bump');
});

test('a catalogueData.ts (typed adapter) change updates /catalogue/\'s <lastmod> AND every catalogue detail page\'s <lastmod> (it is a real rendering dependency of both)', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a', 'wf-b'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = catalogueDataFixtureFor(['wf-a', 'wf-b']);
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/catalogue/'), '2020-01-01');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/catalogue/wf-a/'), '2020-01-01');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/catalogue/wf-b/'), '2020-01-01');

  writeFilesAndCommit(repoRoot, { [AGGREGATE_ADAPTER_REL_PATH]: '// adapter v2\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/catalogue/'), '2020-06-01', 'the aggregate list must bump');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/catalogue/wf-a/'), '2020-06-01', 'every detail page rendered through the adapter must bump');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/catalogue/wf-b/'), '2020-06-01', 'every detail page rendered through the adapter must bump');
});

test('an aggregate catalogue copy (copy/catalogue.ts, already in /catalogue\'s own sourceFiles) change updates /catalogue/\'s <lastmod>', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'), '2020-01-01');

  writeFilesAndCommit(repoRoot, { [AGGREGATE_COPY_REL_PATH]: '// copy v2 — changed list heading/intro copy\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/catalogue/'), '2020-06-01');
});

test('workflow A\'s own change updates /catalogue/ and workflow A\'s own detail page, but not workflow B\'s detail page (aggregate + detail isolation together)', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a', 'wf-b'], '2020-01-01T00:00:00');
  const { distDir } = distFixture();
  const catalogueDataPath = join(dirname(distDir), 'catalogue-data.json');
  writeFileSync(
    catalogueDataPath,
    JSON.stringify({
      workflows: [
        { id: 'wf-a', name: 'wf-a name v1', description: 'wf-a description v1' },
        { id: 'wf-b', name: 'wf-b name v1', description: 'wf-b description v1' },
      ],
    }),
    'utf8',
  );

  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xmlBefore = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xmlBefore, 'https://agentforge4j.org/catalogue/'), '2020-01-01');
  assert.equal(lastmodFor(xmlBefore, 'https://agentforge4j.org/catalogue/wf-a/'), '2020-01-01');
  assert.equal(lastmodFor(xmlBefore, 'https://agentforge4j.org/catalogue/wf-b/'), '2020-01-01');

  writeFilesAndCommit(
    repoRoot,
    { [shippedWorkflowRelPath('wf-a')]: JSON.stringify({ id: 'wf-a', name: 'wf-a name v2', description: 'wf-a description v1' }) },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xmlAfter = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xmlAfter, 'https://agentforge4j.org/catalogue/'), '2020-06-01', 'the aggregate list renders wf-a, so it must bump');
  assert.equal(lastmodFor(xmlAfter, 'https://agentforge4j.org/catalogue/wf-a/'), '2020-06-01', 'workflow A\'s own detail page must bump');
  assert.equal(lastmodFor(xmlAfter, 'https://agentforge4j.org/catalogue/wf-b/'), '2020-01-01', 'workflow B\'s detail page must be unaffected by workflow A\'s change');
});

test('catalogue-only changes (index, workflow files, aggregate deps) do not update unrelated static routes like /architecture/', () => {
  const { repoRoot, seoRoutesPath } = setupCatalogueAggregateRepo(['wf-a'], '2020-01-01T00:00:00');
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/architecture/'), '2020-01-01');

  writeFilesAndCommit(
    repoRoot,
    {
      [SHIPPED_WORKFLOWS_INDEX_REL_PATH]: 'wf-a\nwf-c\n',
      [shippedWorkflowRelPath('wf-c')]: JSON.stringify({ id: 'wf-c', name: 'wf-c', description: 'wf-c' }),
      [AGGREGATE_GENERATOR_REL_PATH]: '// generator v2\n',
      [AGGREGATE_ADAPTER_REL_PATH]: '// adapter v2\n',
    },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  assert.equal(
    lastmodFor(readFileSync(join(distDir, 'sitemap.xml'), 'utf8'), 'https://agentforge4j.org/architecture/'),
    '2020-01-01',
    '/architecture/ shares none of /catalogue/\'s aggregate dependencies and must be completely unaffected',
  );
});

// --- Per-route copy-module dependencies (round 7 finding: /, /architecture, /api, /use,
// /releases, /community, /security, /legal, /contact each render visible text from their own
// copy/*.ts module — previously undeclared) ---------------------------------------------------

// One test repo, nine routes, nine copy files — each round below bumps exactly one route's own
// copy file and re-verifies EVERY route's current expected date, proving both "this route's copy
// change updates it" and "no other route is affected" simultaneously, for every route in one
// pass, rather than nine separate near-identical single-route setups.
const COPY_ONLY_ROUTES = [
  { path: '/', copyRelPath: 'agentforge4j-web-ui/src/copy/home.ts' },
  { path: '/architecture', copyRelPath: 'agentforge4j-web-ui/src/copy/architecture.ts' },
  { path: '/api', copyRelPath: 'agentforge4j-web-ui/src/copy/api.ts' },
  { path: '/use', copyRelPath: 'agentforge4j-web-ui/src/copy/use.ts' },
  { path: '/releases', copyRelPath: 'agentforge4j-web-ui/src/copy/releases.ts' },
  { path: '/community', copyRelPath: 'agentforge4j-web-ui/src/copy/community.ts' },
  { path: '/security', copyRelPath: 'agentforge4j-web-ui/src/copy/security.ts' },
  { path: '/legal', copyRelPath: 'agentforge4j-web-ui/src/copy/legal.ts' },
  { path: '/contact', copyRelPath: 'agentforge4j-web-ui/src/copy/contact.ts' },
];

test('copy-only isolation: changing one route\'s own copy module updates only that route\'s <lastmod> — proven for every static route with a copy dependency (/, /architecture, /api, /use, /releases, /community, /security, /legal, /contact), all pairs at once', () => {
  const repoRoot = initTempGitRepo();
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    routes: COPY_ONLY_ROUTES.map(({ path, copyRelPath }) => ({
      path,
      title: `Title for ${path}`,
      description: `Description for ${path}.`,
      sourceFiles: [copyRelPath],
    })),
  };
  const initialFiles = { [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2) };
  for (const { copyRelPath } of COPY_ONLY_ROUTES) {
    initialFiles[copyRelPath] = '// copy v1\n';
  }
  writeFilesAndCommit(repoRoot, initialFiles, '2020-01-01T00:00:00');
  const seoRoutesPath = join(repoRoot, SEO_ROUTES_REL_PATH);
  const { distDir, catalogueDataPath } = distFixture();

  const expected = Object.fromEntries(COPY_ONLY_ROUTES.map(({ path }) => [path, '2020-01-01']));
  const assertAllRoutesMatch = () => {
    buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
    const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
    for (const { path } of COPY_ONLY_ROUTES) {
      assert.equal(lastmodFor(xml, `https://agentforge4j.org${withTrailingSlash(path)}`), expected[path], `route ${path} <lastmod> mismatch`);
    }
  };
  assertAllRoutesMatch();

  const bumpDates = ['2020-02-01', '2020-03-01', '2020-04-01', '2020-05-01', '2020-06-01', '2020-07-01', '2020-08-01', '2020-09-01', '2020-10-01'];
  COPY_ONLY_ROUTES.forEach(({ path, copyRelPath }, index) => {
    writeFilesAndCommit(repoRoot, { [copyRelPath]: `// copy v2 for ${path}\n` }, `${bumpDates[index]}T00:00:00`);
    expected[path] = bumpDates[index];
    assertAllRoutesMatch();
  });
});

// --- Shared-component dependency (PagePlaceholder — used by /api only today) ------------------

test('a shared component (PagePlaceholder.tsx) change updates every route that actually renders it (today: /api only), and no unrelated route', () => {
  const repoRoot = initTempGitRepo();
  const placeholderRelPath = 'agentforge4j-web-ui/src/components/PagePlaceholder.tsx';
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/api', title: 'API', description: 'API.', sourceFiles: [placeholderRelPath] },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.' },
    ],
  };
  writeFilesAndCommit(
    repoRoot,
    { [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2), [placeholderRelPath]: '// placeholder v1\n' },
    '2020-01-01T00:00:00',
  );
  const seoRoutesPath = join(repoRoot, SEO_ROUTES_REL_PATH);
  const { distDir, catalogueDataPath } = distFixture();
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/api/'), '2020-01-01');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/architecture/'), '2020-01-01');

  writeFilesAndCommit(repoRoot, { [placeholderRelPath]: '// placeholder v2\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(after, 'https://agentforge4j.org/api/'), '2020-06-01', '/api renders PagePlaceholder, so its <lastmod> must bump');
  assert.equal(
    lastmodFor(after, 'https://agentforge4j.org/architecture/'),
    '2020-01-01',
    '/architecture never renders PagePlaceholder, so it must be unaffected',
  );
});

// --- Builder package/lockfile dependency (real repo — the declared range in package.json and the
// exact resolved version pinned in package-lock.json can both change prerendered /builder/
// output) -----------------------------------------------------------------------------------

test('/builder\'s <lastmod> reflects package.json and package-lock.json (the workflow-builder package\'s declared range and pinned resolved version), alongside its own page/copy files', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  const ownFiles = [
    'agentforge4j-web-ui/src/pages/BuilderPage.tsx',
    'agentforge4j-web-ui/src/copy/builder.ts',
    'agentforge4j-web-ui/package.json',
    'agentforge4j-web-ui/package-lock.json',
  ];
  const expectedNewest = newestGitLastModifiedDate(repoRoot, ownFiles);

  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/builder', title: 'Builder', description: 'Builder.', sourceFiles: ownFiles }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const xml = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(xml, 'https://agentforge4j.org/builder/'), expectedNewest);
});

// --- Global shell affects every SPA sitemap route (static and catalogue alike) -----------------

test('a globalSourceFiles change updates every SPA sitemap route — every static route and every catalogue workflow, all at once', () => {
  const repoRoot = initTempGitRepo();
  const globalRelPath = 'agentforge4j-web-ui/src/App.tsx';
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    globalSourceFiles: [globalRelPath],
    routes: [
      { path: '/', title: 'Home', description: 'Home.' },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.' },
      { path: '/security', title: 'Security', description: 'Security.' },
    ],
  };
  const files = {
    [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2),
    [globalRelPath]: '// shell v1\n',
    [SHIPPED_WORKFLOWS_INDEX_REL_PATH]: 'wf-a\n',
    [shippedWorkflowRelPath('wf-a')]: JSON.stringify({ id: 'wf-a', name: 'wf-a', description: 'wf-a' }),
  };
  writeFilesAndCommit(repoRoot, files, '2020-01-01T00:00:00');
  const seoRoutesPath = join(repoRoot, SEO_ROUTES_REL_PATH);
  const { distDir } = distFixture();
  const catalogueDataPath = join(dirname(distDir), 'catalogue-data.json');
  writeFileSync(catalogueDataPath, JSON.stringify({ workflows: [{ id: 'wf-a', name: 'wf-a', description: 'wf-a' }] }), 'utf8');

  const allUrls = [
    'https://agentforge4j.org/',
    'https://agentforge4j.org/architecture/',
    'https://agentforge4j.org/security/',
    'https://agentforge4j.org/catalogue/wf-a/',
  ];
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  for (const url of allUrls) {
    assert.equal(lastmodFor(before, url), '2020-01-01');
  }

  writeFilesAndCommit(repoRoot, { [globalRelPath]: '// shell v2\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  for (const url of allUrls) {
    assert.equal(lastmodFor(after, url), '2020-06-01', `${url} must reflect the global shell change`);
  }
});

// --- artifactGenerationSourceFiles: the build/prerender pipeline itself (index.html, main.tsx,
// build-seo.mjs, prerender-routes.mjs) is a global dependency of every SPA sitemap route, exactly
// like globalSourceFiles — proven with real git commits to stand-in files at each of the real
// pipeline's own paths, not merely by asserting a filename string appears somewhere in JSON. -------

/** A disposable git repo whose committed seo-routes.json declares one entry per
 * `artifactGenerationSourceFiles` scope AND one plain `globalSourceFiles` entry (appRoutes.ts) —
 * shared setup for the per-pipeline-file lastmod tests below, each of which advances exactly one
 * of these files and proves every route (static and catalogue) picks up the change. */
function setupArtifactGenerationRepo() {
  const repoRoot = initTempGitRepo();
  const indexHtmlRelPath = 'agentforge4j-web-ui/index.html';
  const mainTsxRelPath = 'agentforge4j-web-ui/src/main.tsx';
  const buildSeoRelPath = 'agentforge4j-web-ui/scripts/build-seo.mjs';
  const prerenderRelPath = 'agentforge4j-web-ui/scripts/prerender-routes.mjs';
  const appRoutesRelPath = 'agentforge4j-web-ui/src/config/appRoutes.ts';
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    artifactGenerationSourceFiles: [indexHtmlRelPath, mainTsxRelPath, buildSeoRelPath, prerenderRelPath],
    globalSourceFiles: [appRoutesRelPath],
    routes: [
      { path: '/', title: 'Home', description: 'Home.' },
      { path: '/architecture', title: 'Architecture', description: 'Architecture.' },
    ],
  };
  const files = {
    [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2),
    [indexHtmlRelPath]: '<!doctype html>v1\n',
    [mainTsxRelPath]: '// main v1\n',
    [buildSeoRelPath]: '// build-seo v1\n',
    [prerenderRelPath]: '// prerender v1\n',
    [appRoutesRelPath]: '// routes v1\n',
    [SHIPPED_WORKFLOWS_INDEX_REL_PATH]: 'wf-a\n',
    [shippedWorkflowRelPath('wf-a')]: JSON.stringify({ id: 'wf-a', name: 'wf-a', description: 'wf-a' }),
  };
  writeFilesAndCommit(repoRoot, files, '2020-01-01T00:00:00');
  return {
    repoRoot,
    seoRoutesPath: join(repoRoot, SEO_ROUTES_REL_PATH),
    indexHtmlRelPath,
    mainTsxRelPath,
    buildSeoRelPath,
    prerenderRelPath,
    appRoutesRelPath,
  };
}

/** Asserts that advancing exactly one committed file (`relPathToChange`) inside a repo set up by
 * `setupArtifactGenerationRepo` bumps every SPA sitemap route's — static AND catalogue — <lastmod>
 * to the new commit's date. This is the one assertion every pipeline-file test below shares. */
function assertAdvancesEveryRoute(setup, relPathToChange, label) {
  const { distDir } = distFixture();
  const catalogueDataPath = join(dirname(distDir), 'catalogue-data.json');
  writeFileSync(catalogueDataPath, JSON.stringify({ workflows: [{ id: 'wf-a', name: 'wf-a', description: 'wf-a' }] }), 'utf8');
  const allUrls = [
    'https://agentforge4j.org/',
    'https://agentforge4j.org/architecture/',
    'https://agentforge4j.org/catalogue/wf-a/',
  ];

  buildSeo({ distDir, seoRoutesPath: setup.seoRoutesPath, catalogueDataPath, repoRoot: setup.repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  for (const url of allUrls) {
    assert.equal(lastmodFor(before, url), '2020-01-01', `${label}: expected the initial commit date before the change`);
  }

  writeFilesAndCommit(setup.repoRoot, { [relPathToChange]: `// ${label} v2\n` }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath: setup.seoRoutesPath, catalogueDataPath, repoRoot: setup.repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  for (const url of allUrls) {
    assert.equal(lastmodFor(after, url), '2020-06-01', `${label}: ${url} must reflect the change to ${relPathToChange}`);
  }
}

test('changing index.html (the shared shell template every route\'s final HTML derives from) advances every SPA sitemap route', () => {
  const setup = setupArtifactGenerationRepo();
  assertAdvancesEveryRoute(setup, setup.indexHtmlRelPath, 'index.html');
});

test('changing main.tsx (the render entrypoint executed inside the headless browser that produces the prerendered snapshot) advances every SPA sitemap route — the explicitly chosen policy: material, not verification-only', () => {
  const setup = setupArtifactGenerationRepo();
  assertAdvancesEveryRoute(setup, setup.mainTsxRelPath, 'main.tsx');
});

test('changing build-seo.mjs (the shell/sitemap generation implementation itself) advances every SPA sitemap route — classified as material: it decides what every published page\'s HTML actually contains', () => {
  const setup = setupArtifactGenerationRepo();
  assertAdvancesEveryRoute(setup, setup.buildSeoRelPath, 'build-seo.mjs');
});

test('changing prerender-routes.mjs (the prerender-capture implementation itself) advances every SPA sitemap route — classified as material: it decides what markup gets captured for every route', () => {
  const setup = setupArtifactGenerationRepo();
  assertAdvancesEveryRoute(setup, setup.prerenderRelPath, 'prerender-routes.mjs');
});

test('changing appRoutes.ts (the path -> component REGISTRY App.tsx renders from) advances every SPA sitemap route — the real semantic dependency, not merely a filename appearing in JSON: appRoutes.ts is what decides which component actually renders at a given path, independent of App.tsx\'s own text', () => {
  const setup = setupArtifactGenerationRepo();
  assertAdvancesEveryRoute(setup, setup.appRoutesRelPath, 'appRoutes.ts');
});

test('a route-registry change cannot leave a changed rendered route with a stale lastmod: swapping which page component a path maps to in appRoutes.ts is exactly the scenario globalSourceFiles tracking this file protects against', () => {
  // Simulates the real defect this fix closes: before appRoutes.ts was tracked, editing which
  // Component a path renders (e.g. pointing /architecture at a completely different page) changed
  // that route's actual published HTML while every one of its declared sourceFiles (the ORIGINAL
  // page component + copy file) stayed byte-identical — so its <lastmod> would never have moved.
  const repoRoot = initTempGitRepo();
  const appRoutesRelPath = 'agentforge4j-web-ui/src/config/appRoutes.ts';
  const architecturePageRelPath = 'agentforge4j-web-ui/src/pages/ArchitecturePage.tsx';
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    globalSourceFiles: [appRoutesRelPath],
    routes: [{ path: '/architecture', title: 'Architecture', description: 'Architecture.', sourceFiles: [architecturePageRelPath] }],
  };
  const files = {
    [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2),
    [appRoutesRelPath]: "{ path: '/architecture', Component: ArchitecturePage }\n",
    [architecturePageRelPath]: '// ArchitecturePage v1 — untouched throughout this test\n',
  };
  writeFilesAndCommit(repoRoot, files, '2020-01-01T00:00:00');
  const seoRoutesPath = join(repoRoot, SEO_ROUTES_REL_PATH);
  const { distDir, catalogueDataPath } = distFixture();

  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/architecture/'), '2020-01-01');

  // The registry mapping changes (a different component would now render here) — ArchitecturePage.tsx
  // itself, and every one of /architecture's own declared sourceFiles, remain completely untouched.
  writeFilesAndCommit(
    repoRoot,
    { [appRoutesRelPath]: "{ path: '/architecture', Component: SomeOtherPage }\n" },
    '2020-06-01T00:00:00',
  );
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(
    lastmodFor(after, 'https://agentforge4j.org/architecture/'),
    '2020-06-01',
    'the route-registry change alone must advance /architecture — its own sourceFiles never changed',
  );
});

test('a totally unrelated, undeclared file has zero effect on any route\'s <lastmod> — this mechanism only ever reads explicitly declared dependencies, never the whole repository', () => {
  const repoRoot = initTempGitRepo();
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/', title: 'Home', description: 'Home.', sourceFiles: ['agentforge4j-web-ui/src/pages/HomePage.tsx'] }],
  };
  const unrelatedRelPath = 'agentforge4j-docs/docs/some-unrelated-doc.mdx';
  const files = {
    [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2),
    'agentforge4j-web-ui/src/pages/HomePage.tsx': '// HomePage v1\n',
    [unrelatedRelPath]: '# Unrelated doc v1\n',
  };
  writeFilesAndCommit(repoRoot, files, '2020-01-01T00:00:00');
  const seoRoutesPath = join(repoRoot, SEO_ROUTES_REL_PATH);
  const { distDir, catalogueDataPath } = distFixture();

  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const before = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(lastmodFor(before, 'https://agentforge4j.org/'), '2020-01-01');

  writeFilesAndCommit(repoRoot, { [unrelatedRelPath]: '# Unrelated doc v2 — a real, later commit\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(
    lastmodFor(after, 'https://agentforge4j.org/'),
    '2020-01-01',
    'an undeclared, unrelated file\'s later commit must never advance an unrelated route\'s <lastmod>',
  );
});

test('verification-only code (verify-seo.mjs) is correctly excluded from the dependency model: it is not declared anywhere, and its own changes never advance any route\'s <lastmod> — it checks the built artifact, it does not produce or alter it', () => {
  const repoRoot = initTempGitRepo();
  const routesContent = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/', title: 'Home', description: 'Home.', sourceFiles: ['agentforge4j-web-ui/src/pages/HomePage.tsx'] }],
  };
  const verifySeoRelPath = 'agentforge4j-web-ui/scripts/verify-seo.mjs';
  const files = {
    [SEO_ROUTES_REL_PATH]: JSON.stringify(routesContent, null, 2),
    'agentforge4j-web-ui/src/pages/HomePage.tsx': '// HomePage v1\n',
    [verifySeoRelPath]: '// verify-seo v1\n',
  };
  writeFilesAndCommit(repoRoot, files, '2020-01-01T00:00:00');
  const seoRoutesPath = join(repoRoot, SEO_ROUTES_REL_PATH);
  const { distDir, catalogueDataPath } = distFixture();

  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  writeFilesAndCommit(repoRoot, { [verifySeoRelPath]: '// verify-seo v2\n' }, '2020-06-01T00:00:00');
  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot });
  const after = readFileSync(join(distDir, 'sitemap.xml'), 'utf8');
  assert.equal(
    lastmodFor(after, 'https://agentforge4j.org/'),
    '2020-01-01',
    'verify-seo.mjs is verification-only and must never itself be a lastmod dependency of any route',
  );
});

// --- Fail loudly on a declared-but-missing dependency (typo/stale entry) -----------------------

test('fails loudly when a route declares a sourceFiles entry that does not exist (a typo or stale entry), rather than silently degrading its <lastmod>', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/', title: 'Home', description: 'Home.', sourceFiles: ['agentforge4j-web-ui/src/copy/this-file-does-not-exist.ts'] }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot: join(REAL_MODULE_ROOT, '..') }),
    /sourceFiles declares "agentforge4j-web-ui\/src\/copy\/this-file-does-not-exist\.ts", which does not exist/,
  );
});

test('fails loudly when artifactGenerationSourceFiles/globalSourceFiles/catalogueSourceFiles/aggregateCatalogueSourceFiles declares a nonexistent entry', () => {
  const repoRoot = join(REAL_MODULE_ROOT, '..');
  for (const [key, badList] of [
    ['artifactGenerationSourceFiles', ['agentforge4j-web-ui/src/nope.ts']],
    ['globalSourceFiles', ['agentforge4j-web-ui/src/nope.ts']],
    ['catalogueSourceFiles', ['agentforge4j-web-ui/src/nope.ts']],
    ['aggregateCatalogueSourceFiles', ['agentforge4j-web-ui/src/nope.ts']],
  ]) {
    const routes = {
      siteUrl: 'https://agentforge4j.org',
      [key]: badList,
      routes: [{ path: '/', title: 'Home', description: 'Home.' }],
    };
    const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
    assert.throws(
      () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot }),
      new RegExp(`${key} declares "agentforge4j-web-ui/src/nope.ts"`.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
      `expected ${key} to fail loudly on a nonexistent entry`,
    );
  }
});

// --- Completeness guard: every real copy module on disk is referenced by something in the real
// seo-routes.json — catches a future page/copy addition that forgot to wire up its dependency
// mapping (no AST/import parsing: a plain "is this real file referenced anywhere" check) --------

test('completeness guard: every real file under agentforge4j-web-ui/src/copy/ is referenced by at least one entry in the real committed seo-routes.json (a new copy module with no route/scope wiring it up would fail here)', () => {
  const copyDir = join(REAL_MODULE_ROOT, 'src', 'copy');
  const copyFiles = readdirSync(copyDir).map((name) => `agentforge4j-web-ui/src/copy/${name}`);
  assert.ok(copyFiles.length > 0, 'expected at least one real copy file to check');

  const { artifactGenerationSourceFiles, globalSourceFiles, catalogueSourceFiles, aggregateCatalogueSourceFiles, routes } = JSON.parse(
    readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'),
  );
  const declared = new Set([
    ...artifactGenerationSourceFiles,
    ...globalSourceFiles,
    ...catalogueSourceFiles,
    ...aggregateCatalogueSourceFiles,
    ...routes.flatMap((route) => route.sourceFiles ?? []),
  ]);

  for (const copyFile of copyFiles) {
    assert.ok(
      declared.has(copyFile),
      `${copyFile} exists on disk but is not referenced anywhere in seo-routes.json — a change to it would silently ` +
        'never bump any route\'s <lastmod>',
    );
  }
});

// --- Global dependency scope contract: every file KNOWN to control every route's rendered output
// (traced during this design's dependency audit) must appear in artifactGenerationSourceFiles or
// globalSourceFiles in the real committed seo-routes.json — protects against a future reviewer
// having to re-discover one of these one file at a time, the exact failure mode that produced this
// fix in the first place (appRoutes.ts was the first instance; this guard is broader). -----------

test('global dependency scope contract: every file traced as materially affecting every route\'s output is present in artifactGenerationSourceFiles or globalSourceFiles', () => {
  const { artifactGenerationSourceFiles, globalSourceFiles } = JSON.parse(
    readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'),
  );
  const declaredGlobally = new Set([...artifactGenerationSourceFiles, ...globalSourceFiles]);

  // Build/prerender pipeline: decides what "the rendered page" even is, for every route at once.
  const expectedArtifactGeneration = [
    'agentforge4j-web-ui/index.html',
    'agentforge4j-web-ui/src/main.tsx',
    'agentforge4j-web-ui/scripts/build-seo.mjs',
    'agentforge4j-web-ui/scripts/prerender-routes.mjs',
  ];
  // Shared React render surface: the actual component tree and routing registry every page renders
  // through.
  const expectedGlobalRender = [
    'agentforge4j-web-ui/src/App.tsx',
    'agentforge4j-web-ui/src/config/appRoutes.ts',
    'agentforge4j-web-ui/src/components/SiteHeader.tsx',
    'agentforge4j-web-ui/src/components/SiteFooter.tsx',
    'agentforge4j-web-ui/src/config/nav.ts',
  ];

  for (const relFile of [...expectedArtifactGeneration, ...expectedGlobalRender]) {
    assert.ok(
      existsSync(join(REAL_MODULE_ROOT, '..', relFile)),
      `${relFile} is expected by this contract test but no longer exists on disk — update this test, not just seo-routes.json`,
    );
    assert.ok(
      declaredGlobally.has(relFile),
      `${relFile} is known to materially affect every route's rendered output but is missing from both ` +
        'artifactGenerationSourceFiles and globalSourceFiles in seo-routes.json',
    );
  }

  for (const relFile of expectedArtifactGeneration) {
    assert.ok(
      artifactGenerationSourceFiles.includes(relFile),
      `${relFile} belongs in artifactGenerationSourceFiles specifically (the build/prerender pipeline), not just anywhere global`,
    );
  }
  for (const relFile of expectedGlobalRender) {
    assert.ok(
      globalSourceFiles.includes(relFile),
      `${relFile} belongs in globalSourceFiles specifically (the shared render surface), not just anywhere global`,
    );
  }
});

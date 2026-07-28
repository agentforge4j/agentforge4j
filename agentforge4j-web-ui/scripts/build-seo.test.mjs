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
// `URL` is imported explicitly rather than leaned on as an ambient global — the same thing
// prerender-routes.test.mjs already does, and what this repo's lint block for `scripts/**/*.mjs`
// requires (it declares only console/process/Buffer/fetch as globals).
import { fileURLToPath, URL } from 'node:url';
import {
  buildSeo,
  escapeHtml,
  gitLastModifiedDate,
  gitLastModifiedDateForRouteMetadata,
  injectHead,
  injectJsonLd,
  injectNotFoundHead,
  injectRoot,
  JSON_LD_SCRIPT_ID,
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

test('injectRoot ships `$`-sequences in the captured markup verbatim, never as replacement patterns', () => {
  // Serialized DOM markup can legitimately contain `$&`, "$`", `$'`, and `$$` (page copy, inline
  // code samples) — String.prototype.replace treats all four as substitution patterns when the
  // replacement is a plain string, silently splicing shell fragments into the page. Deterministic
  // corruption, so the prerenderer's double-capture equality gate would never catch it.
  const markup = '<main><h1>Regex &amp; refs: $&amp; raw $& backref $\' dollar $$ tick $` end</h1></main>';
  const html = injectRoot(BASE_INDEX_HTML, markup);
  assert.ok(html.includes(`<div id="root">${markup}</div>`), 'captured markup must ship byte-for-byte as given');
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
  const match = /<script id="([^"]*)" type="application\/ld\+json">([\s\S]*?)<\/script>\s*<\/head>/.exec(html);
  assert.ok(match, 'expected a JSON-LD script immediately before </head>');
  assert.deepEqual(JSON.parse(match[2]), jsonLd);
});

test('injectJsonLd\'s script carries the exact id usePageSeo.ts\'s client-side setJsonLd looks for, so hydration adopts it instead of creating a duplicate', () => {
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  const html = injectJsonLd(BASE_INDEX_HTML, jsonLd);
  const match = /<script id="([^"]*)" type="application\/ld\+json">/.exec(html);
  assert.ok(match, 'expected a JSON-LD script tag');
  assert.equal(match[1], JSON_LD_SCRIPT_ID);
});

test('injectJsonLd is a no-op for routes that declare no jsonLd (every route except "/")', () => {
  assert.equal(injectJsonLd(BASE_INDEX_HTML, undefined), BASE_INDEX_HTML);
});

test('injectJsonLd fails closed when the shell has no </head> to insert before (template drift)', () => {
  // The sibling of injectRoot's own fail-closed test above, for the same reason: this throw is the
  // only thing standing between an index.html whose structure has drifted and a homepage shell that
  // silently ships no structured data at all. A regression turning it into a quiet `return html`
  // would otherwise surface only indirectly, at verify-seo's real-dist check, rather than in the
  // unit that owns the guard.
  const noHead = BASE_INDEX_HTML.replace('</head>', '');
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' };
  assert.throws(() => injectJsonLd(noHead, jsonLd), /expected a <\/head> closing tag/);
});

/** `<script` open tags in a document. The assertions below compare this as a DELTA against the
 * base shell rather than against an absolute number: BASE_INDEX_HTML happens to carry none, but
 * the real built index.html carries two (the theme-bootstrap inline script and the module
 * entrypoint), so an absolute "exactly 1" would only ever have been a statement about this
 * fixture's shape, not about what injectJsonLd does to a real shell. */
function countScriptOpenTags(html) {
  return (html.match(/<script[ >]/g) ?? []).length;
}

test('injectJsonLd cannot be broken out of the <script> body by a value containing a literal </script> sequence', () => {
  const jsonLd = { '@context': 'https://schema.org', '@type': 'WebSite', description: 'a</script><script>alert(1)</script>' };
  const html = injectJsonLd(BASE_INDEX_HTML, jsonLd);
  // Exactly one <script> more than the shell already had: the one this function wrote, and none
  // injected via the JSON-LD payload's own string content.
  assert.equal(
    countScriptOpenTags(html) - countScriptOpenTags(BASE_INDEX_HTML),
    1,
    'expected exactly one added <script> — the JSON-LD one — and none from the payload',
  );
  // The literal, unescaped sequence must never appear in the emitted HTML at all.
  assert.ok(!html.includes('</script><script>alert(1)</script>'), 'the raw </script> sequence from the JSON-LD value must not reach the HTML unescaped');
  // Semantics are unchanged: parsing the actual emitted JSON-LD block back out still yields the
  // exact original string, </script> and all — this is an encoding fix, not a content change.
  const match = /<script id="([^"]*)" type="application\/ld\+json">([\s\S]*?)<\/script>\s*<\/head>/.exec(html);
  assert.ok(match, 'expected a JSON-LD script immediately before </head>');
  assert.deepEqual(JSON.parse(match[2]), jsonLd);
});

// The escaping above is only worth anything if the escaped text actually reaches the HTML
// unchanged. `String.prototype.replace` expands `$&`, "$`", `$'` and `$$` in a replacement STRING
// *after* any escaping has already run, so passing the serialized JSON as one would re-inject raw
// document text — the body's own `</script>` included — straight past the escaper, deterministically
// and with no error. injectRoot carries the identical guard, for the identical reason; this test is
// the injectJsonLd half of it.
test('injectJsonLd preserves $-substitution tokens verbatim rather than letting String.replace expand them into document text', () => {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'A$&B',
    alternateName: 'C$`D',
    description: "E$'F",
    slogan: 'G$$H',
  };
  const html = injectJsonLd(BASE_INDEX_HTML, jsonLd);
  const match = /<script id="([^"]*)" type="application\/ld\+json">([\s\S]*?)<\/script>\s*<\/head>/.exec(html);
  assert.ok(match, 'expected a JSON-LD script immediately before </head>');
  // Every token survives as itself: `$&` did not become the matched `</head>`, "$`" did not become
  // the whole preceding document, `$'` did not become the whole following one, and `$$` did not
  // collapse to a single `$`.
  assert.deepEqual(JSON.parse(match[2]), jsonLd);
  // Each expansion has its own unmistakable footprint in the document structure, so assert on all
  // three rather than trusting the round-trip alone to have covered them.
  assert.equal((html.match(/<\/head>/g) ?? []).length, 1, '`$&` must not have spliced a second </head> into the document');
  assert.equal((html.match(/<head>/g) ?? []).length, 1, '"$`" must not have spliced a copy of the preceding document into the head');
  assert.equal((html.match(/<body>/g) ?? []).length, 1, "`$'` must not have spliced a copy of the following document into the head");
  assert.equal(
    countScriptOpenTags(html) - countScriptOpenTags(BASE_INDEX_HTML),
    1,
    'expected exactly one added <script> — no document markup smuggled in by token expansion',
  );
});

test('buildSeo splices the "/" route\'s jsonLd (seo-routes.json) into the produced index.html shell (BASE_INDEX_HTML fixture), and no other route gets one — see verify-seo.test.mjs for the real dist/ output check', () => {
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

// --- Per-route copy-module dependencies (/, /architecture, /api, /use,
// /releases, /community, /security, /legal, /contact each render visible text from their own
// copy/*.ts module) ----------------------------------------------------------------------------

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

// --- Fail loudly on a malformed jsonLd config, rather than silently shipping unusable structured
// data (a typo'd config surface must fail the build, exactly like a stale sourceFiles entry) -----

test('fails loudly when a route\'s jsonLd is not an object at all (e.g. a stray string where an object was meant)', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/', title: 'Home', description: 'Home.', jsonLd: 'not-a-structured-data-object' }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
    /route "\/"'s jsonLd must be a plain object/,
  );
});

test('fails loudly when a route\'s jsonLd is an empty object (no real structured data at all)', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/', title: 'Home', description: 'Home.', jsonLd: {} }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
    /missing a non-empty "@context" string/,
  );
});

test('fails loudly when a route\'s jsonLd has a @context but neither a real @type nor a real @graph', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [{ path: '/', title: 'Home', description: 'Home.', jsonLd: { '@context': 'https://schema.org' } }],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
    /must declare a non-empty "@type" string, or a non-empty "@graph"/,
  );
});

test('fails loudly when a route\'s jsonLd @graph entry itself has no real @type', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      {
        path: '/',
        title: 'Home',
        description: 'Home.',
        jsonLd: { '@context': 'https://schema.org', '@graph': [{ name: 'no type here' }] },
      },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
    /declares "@graph" but it is not a non-empty array whose every entry itself declares a non-empty "@type"/,
  );
});

// --- Every remaining rejection branch, table-driven. The four hand-written cases above cover the
// shapes a real typo most often takes; these cover the rest of the predicate, so a future refactor
// cannot quietly drop a branch while the suite stays green. assertValidJsonLd is the build's ONLY
// structured-data validity gate — nothing downstream re-checks the config's shape. ---------------

const MALFORMED_JSON_LD_CASES = [
  // Not a plain object: the array and null forms the string case above does not reach.
  { label: 'an array', jsonLd: [], expected: /jsonLd must be a plain object/ },
  { label: 'null', jsonLd: null, expected: /jsonLd must be a plain object/ },
  // @context present but not a usable string.
  { label: 'an empty-string @context', jsonLd: { '@context': '', '@type': 'WebSite' }, expected: /missing a non-empty "@context" string/ },
  { label: 'a non-string @context', jsonLd: { '@context': 123, '@type': 'WebSite' }, expected: /missing a non-empty "@context" string/ },
  // @type present but not a usable string (the non-string form is covered above, alongside a valid
  // @graph; this is the empty-string form, and standalone).
  {
    label: 'an empty-string @type',
    jsonLd: { '@context': 'https://schema.org', '@type': '' },
    expected: /declares "@type" but it is not a non-empty string/,
  },
  // @graph present but not a non-empty array of typed nodes — every sub-condition of the predicate.
  { label: 'an empty @graph array', jsonLd: { '@context': 'https://schema.org', '@graph': [] }, expected: /declares "@graph" but it is not a non-empty array/ },
  { label: 'a non-array @graph', jsonLd: { '@context': 'https://schema.org', '@graph': 'not-an-array' }, expected: /declares "@graph" but it is not a non-empty array/ },
  { label: 'a null @graph entry', jsonLd: { '@context': 'https://schema.org', '@graph': [null] }, expected: /declares "@graph" but it is not a non-empty array/ },
  { label: 'an array @graph entry', jsonLd: { '@context': 'https://schema.org', '@graph': [[]] }, expected: /declares "@graph" but it is not a non-empty array/ },
  {
    label: 'a @graph entry whose @type is an empty string',
    jsonLd: { '@context': 'https://schema.org', '@graph': [{ '@type': '' }] },
    expected: /declares "@graph" but it is not a non-empty array/,
  },
];

for (const { label, jsonLd, expected } of MALFORMED_JSON_LD_CASES) {
  test(`fails loudly when a route's jsonLd is ${label}`, () => {
    const routes = {
      siteUrl: 'https://agentforge4j.org',
      routes: [{ path: '/', title: 'Home', description: 'Home.', jsonLd }],
    };
    const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
    assert.throws(() => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }), expected);
  });
}

// --- @type and @graph are each validated independently when the key is present — a valid one must
// never let an invalid other one through just because the OR as a whole would otherwise be
// satisfied (a plain `hasType || hasValidGraph` check would let either key's own garbage value
// through undetected as long as the other key looked fine) -----------------------------------------

test('fails loudly when jsonLd has a real, valid @type but an invalid @graph present alongside it (a valid @type must not hide a malformed @graph)', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      {
        path: '/',
        title: 'Home',
        description: 'Home.',
        jsonLd: { '@context': 'https://schema.org', '@type': 'WebSite', '@graph': [{ name: 'no type here' }] },
      },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
    /declares "@graph" but it is not a non-empty array whose every entry itself declares a non-empty "@type"/,
  );
});

test('fails loudly when jsonLd has a real, valid @graph but an invalid (non-string) @type present alongside it (a valid @graph must not hide a malformed @type)', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      {
        path: '/',
        title: 'Home',
        description: 'Home.',
        jsonLd: { '@context': 'https://schema.org', '@type': 123, '@graph': [{ '@type': 'WebPage' }] },
      },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.throws(
    () => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }),
    /declares "@type" but it is not a non-empty string/,
  );
});

test('accepts a real jsonLd with both a valid @type and a valid @graph present together', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      {
        path: '/',
        title: 'Home',
        description: 'Home.',
        jsonLd: { '@context': 'https://schema.org', '@type': 'WebSite', '@graph': [{ '@type': 'WebPage' }] },
      },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.doesNotThrow(() => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }));
});

test('accepts a real single-node jsonLd (an @type, no @graph)', () => {
  const routes = {
    siteUrl: 'https://agentforge4j.org',
    routes: [
      { path: '/', title: 'Home', description: 'Home.', jsonLd: { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' } },
    ],
  };
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({ routes });
  assert.doesNotThrow(() => buildSeo({ distDir, seoRoutesPath, catalogueDataPath }));
});

// --- Self-referencing JSON-LD URLs stay bound to the same siteUrl every canonical/OG/sitemap URL
// is derived from. injectJsonLd ships a route's jsonLd verbatim and verify-seo compares served
// against declared — both sides being the same config block, neither can notice that the block's
// own hardcoded origin has drifted away from siteUrl. Keyed on the three self-referential
// properties (`@id`, `url`, `logo`) rather than on every URL-shaped string, because `sameAs`,
// `codeRepository` and `license` are deliberately EXTERNAL (github.com, apache.org) and must stay
// that way. ----------------------------------------------------------------------------------

const SELF_REFERENTIAL_KEYS = new Set(['@id', 'url', 'logo']);

/** Compares ORIGINS, never string prefixes. `"https://agentforge4j.org.example.com/".startsWith(
 * "https://agentforge4j.org")` is `true`, so a prefix test would wave through a value on a
 * different host that merely begins with the site's own — the same trap verify-seo.mjs's
 * `resolveWithinRoot` documents rejecting for filesystem paths (`dist-evil` next to `dist`), for
 * the same reason: the boundary between "inside" and "outside" is a structural one, and a prefix
 * test does not know where it falls.
 *
 * Every reference is RESOLVED against `siteUrl` before its origin is read, rather than parsed
 * standalone. Parsing standalone would mean asking "is this an absolute URL?" and treating every
 * answer of "no" as on-site — which is true of a relative-path (`/brand/icon-512.png`) or fragment
 * (`#software`) reference, but flatly false of a NETWORK-PATH one: `//cdn.example.com/logo.png` is
 * a relative reference that inherits only the scheme, so it names a different host while failing a
 * standalone `new URL()` outright. Resolving first collapses all three forms onto the one question
 * that actually matters — what origin does a consumer end up at — so the genuinely origin-less
 * forms resolve to `siteOrigin` and stay non-offenders, while the network-path form is caught. The
 * `catch` then narrows to what it should always have meant: a value that is not a URL reference at
 * all. */
function isOffSite(value, siteUrl, siteOrigin) {
  let parsed;
  try {
    parsed = new URL(value, siteUrl);
  } catch {
    return false;
  }
  return parsed.origin !== siteOrigin;
}

/** The URL strings a self-referential key can carry, as `{ url, trail }` pairs: a bare string, or
 * the string members of an array (schema.org permits either — an array-valued `url`/`logo` is
 * ordinary). Reading only the bare string would leave an off-origin value inside an array
 * completely unchecked, which is the whole failure this guard exists to catch.
 *
 * Mirrors verify-seo.mjs's `assetUrlStrings`, which does the same shape flattening for
 * `logo`/`image`: the two walk the same tree and must not cover different shapes. The one case
 * that file needs and this one does not is the `ImageObject` form — such a node's own `url` is
 * itself a self-referential key, so the walk below already reaches it by recursion. */
function selfReferentialUrls(value, trail) {
  if (typeof value === 'string') {
    return [{ url: value, trail }];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item, index) =>
      typeof item === 'string' ? [{ url: item, trail: `${trail}[${index}]` }] : [],
    );
  }
  return [];
}

/** Every `@id`/`url`/`logo` anywhere in any route's `jsonLd` whose origin is not `siteUrl`'s,
 * as human-readable `route "<path>": <trail> = <value>` strings. Shared by the real-config guard
 * and its own negative test below, so the two can never check different things. */
function offSiteSelfReferencingUrls(routes, siteUrl) {
  const siteOrigin = new URL(siteUrl).origin;
  const offenders = [];
  const walk = (node, routePath, trail) => {
    if (Array.isArray(node)) {
      node.forEach((item, index) => walk(item, routePath, `${trail}[${index}]`));
      return;
    }
    if (node === null || typeof node !== 'object') {
      return;
    }
    for (const [key, value] of Object.entries(node)) {
      const here = trail ? `${trail}.${key}` : key;
      if (SELF_REFERENTIAL_KEYS.has(key)) {
        for (const candidate of selfReferentialUrls(value, here)) {
          if (isOffSite(candidate.url, siteUrl, siteOrigin)) {
            offenders.push(`route "${routePath}": ${candidate.trail} = ${JSON.stringify(candidate.url)}`);
          }
        }
      }
      walk(value, routePath, here);
    }
  };
  for (const route of routes) {
    walk(route.jsonLd, route.path, '');
  }
  return offenders;
}

test('every self-referencing URL (@id/url/logo) in the real committed seo-routes.json jsonLd sits on siteUrl\'s own origin, so a siteUrl change can never leave structured data pointing at the old origin while the canonical follows the new one', () => {
  const { siteUrl, routes } = JSON.parse(readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'));
  const offenders = offSiteSelfReferencingUrls(routes, siteUrl);

  assert.deepEqual(
    offenders,
    [],
    `every @id/url/logo in a route's jsonLd must sit on siteUrl's origin (${siteUrl}) — offenders:\n${offenders.join('\n')}`,
  );
  // Not vacuous: the real config must actually declare some of these, or this guard would pass on
  // an empty walk and quietly stop protecting anything.
  const selfReferentialCount = routes
    .filter((route) => route.jsonLd)
    .flatMap((route) => JSON.stringify(route.jsonLd).match(/"(@id|url|logo)":/g) ?? []).length;
  assert.ok(selfReferentialCount > 0, 'expected the real seo-routes.json to declare at least one @id/url/logo in a jsonLd block');
});

test('the self-referencing-URL guard rejects a different host that merely starts with siteUrl\'s string — an origin check, not a prefix check', () => {
  const siteUrl = 'https://agentforge4j.org';
  const routes = [
    {
      path: '/',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          // Genuinely on-site: bare origin, a real path, and a fragment id.
          { '@type': 'WebSite', '@id': `${siteUrl}/#website`, url: `${siteUrl}/` },
          { '@type': 'Organization', logo: `${siteUrl}/brand/icon-512.png` },
          // A DIFFERENT host whose name merely begins with the site's own. `startsWith` says yes.
          { '@type': 'Organization', '@id': 'https://agentforge4j.org.example.com/#organization' },
        ],
      },
    },
  ];

  assert.deepEqual(offSiteSelfReferencingUrls(routes, siteUrl), [
    'route "/": @graph[2].@id = "https://agentforge4j.org.example.com/#organization"',
  ]);
  // The trap this exists to close: the rejected value passes a bare prefix comparison outright.
  assert.ok('https://agentforge4j.org.example.com/#organization'.startsWith(siteUrl));
});

test('the self-referencing-URL guard rejects a network-path (protocol-relative) reference, which names a different host while failing a standalone URL parse', () => {
  const siteUrl = 'https://agentforge4j.org';
  const routes = [
    {
      path: '/',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          // `//host/path` inherits only the scheme, so it resolves to a DIFFERENT origin — but
          // `new URL('//cdn.example.com/logo.png')` with no base throws, so a standalone parse
          // would classify it as "not an absolute URL" and wave it through as on-site.
          { '@type': 'Organization', logo: '//cdn.example.com/brand/icon-512.png' },
        ],
      },
    },
  ];

  assert.deepEqual(offSiteSelfReferencingUrls(routes, siteUrl), [
    'route "/": @graph[0].logo = "//cdn.example.com/brand/icon-512.png"',
  ]);
  // The trap this exists to close, stated mechanically: parsed standalone the value is not a URL
  // at all, yet resolved against the real document base it lands on someone else's host.
  assert.throws(() => new URL('//cdn.example.com/brand/icon-512.png'));
  assert.equal(new URL('//cdn.example.com/brand/icon-512.png', siteUrl).origin, 'https://cdn.example.com');
});

test('the self-referencing-URL guard reads an ARRAY-valued @id/url/logo, not only a bare string — an off-origin member inside an array is still an offender', () => {
  // schema.org permits an array wherever it permits a single value, so `logo: [a, b]` is ordinary
  // rather than exotic. A guard that tested `typeof value === 'string'` and stopped saw none of
  // these: the array is not a string, and the members are strings whose own recursion never
  // re-examines the key they arrived under. verify-seo.mjs's sibling asset walk reads the array
  // form, so leaving it out here would have meant the two guards over the same tree disagreeing
  // about which shapes exist.
  const siteUrl = 'https://agentforge4j.org';
  const routes = [
    {
      path: '/',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          {
            '@type': 'Organization',
            logo: [`${siteUrl}/brand/icon-512.png`, 'https://cdn.example.com/brand/other.png'],
            url: ['//cdn.example.com/', `${siteUrl}/`],
          },
        ],
      },
    },
  ];

  // Reported per offending member, with its own index — not as one opaque "the array is wrong".
  assert.deepEqual(offSiteSelfReferencingUrls(routes, siteUrl), [
    'route "/": @graph[0].logo[1] = "https://cdn.example.com/brand/other.png"',
    'route "/": @graph[0].url[0] = "//cdn.example.com/"',
  ]);
});

test('the self-referencing-URL guard accepts an array whose every member is on-site, and ignores non-string members', () => {
  const siteUrl = 'https://agentforge4j.org';
  const routes = [
    {
      path: '/',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          {
            '@type': 'Organization',
            logo: [`${siteUrl}/brand/icon-512.png`, '/brand/logo-light.png'],
            // A nested node is not a URL string; the walk reaches its own `url` by recursion
            // instead, so it must not be reported twice or mis-indexed here.
            image: [{ '@type': 'ImageObject', url: `${siteUrl}/brand/social.png` }],
          },
        ],
      },
    },
  ];

  assert.deepEqual(offSiteSelfReferencingUrls(routes, siteUrl), []);
});

test('the self-referencing-URL guard leaves deliberately external properties (sameAs/codeRepository/license) alone, and does not flag a relative IRI', () => {
  const siteUrl = 'https://agentforge4j.org';
  const routes = [
    {
      path: '/',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          {
            '@type': 'SoftwareSourceCode',
            sameAs: ['https://github.com/agentforge4j'],
            codeRepository: 'https://github.com/agentforge4j/agentforge4j',
            license: 'https://www.apache.org/licenses/LICENSE-2.0',
            // A relative IRI resolves against the document's own base, so it cannot name a
            // different origin and must not be reported.
            '@id': '#software',
          },
        ],
      },
    },
  ];

  assert.deepEqual(offSiteSelfReferencingUrls(routes, siteUrl), []);
});

// --- A route's structured data restates the route's own `description` verbatim. That one sentence
// is the page's canonical summary — injectHead writes it into `<meta name="description">`,
// `og:description` and `twitter:description` — and a route's `jsonLd` declares it a SECOND time, by
// hand. Nothing else can notice when the two drift: assertValidJsonLd never inspects prose,
// verify-seo compares served against declared (the same block on both sides), and the
// self-referencing-URL guard above covers only `@id`/`url`/`logo`. So a routine copy reword would
// leave the structured data describing the page differently from the page's own meta description,
// with every gate green. Deliberately "appears SOMEWHERE in the block", not "every description in
// the block equals it": a graph legitimately describes several entities, and today's
// SoftwareSourceCode node carries its own distinct description of the framework rather than of the
// page. ----------------------------------------------------------------------------------------

/** Every `description` string anywhere in `jsonLd`, in document order. */
function collectDescriptions(node, out = []) {
  if (Array.isArray(node)) {
    node.forEach((item) => collectDescriptions(item, out));
    return out;
  }
  if (node === null || typeof node !== 'object') {
    return out;
  }
  for (const [key, value] of Object.entries(node)) {
    if (key === 'description' && typeof value === 'string') {
      out.push(value);
    }
    collectDescriptions(value, out);
  }
  return out;
}

/** Every route declaring a `jsonLd` whose structured data does not restate that route's own
 * `description` verbatim, as human-readable strings. Shared by the real-config guard and its own
 * negative test below, so the two can never check different things. */
function routesWhoseJsonLdOmitsTheirOwnDescription(routes) {
  return routes
    .filter((route) => route.jsonLd !== undefined)
    .filter((route) => !collectDescriptions(route.jsonLd).includes(route.description))
    .map(
      (route) =>
        `route "${route.path}": description ${JSON.stringify(route.description)} appears in no jsonLd node ` +
        `(found: ${JSON.stringify(collectDescriptions(route.jsonLd))})`,
    );
}

test('every route\'s jsonLd restates that route\'s own description verbatim, so a reworded page description can never leave the structured data describing the page differently from its own meta description', () => {
  const { routes } = JSON.parse(readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'));
  const offenders = routesWhoseJsonLdOmitsTheirOwnDescription(routes);

  assert.deepEqual(offenders, [], `structured data must restate the route's own description:\n${offenders.join('\n')}`);
  // Not vacuous: at least one real route must actually declare a jsonLd, or this guard would pass
  // on an empty filter and quietly stop protecting anything.
  assert.ok(
    routes.some((route) => route.jsonLd !== undefined),
    'expected the real seo-routes.json to declare at least one jsonLd block',
  );
});

test('the description-binding guard fires when a route\'s description is reworded but its jsonLd copy is not', () => {
  const drifted = [
    {
      path: '/',
      description: 'The reworded page description.',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          { '@type': 'WebSite', description: 'The ORIGINAL page description.' },
          { '@type': 'SoftwareSourceCode', description: 'A deliberately different one, about the framework.' },
        ],
      },
    },
  ];

  assert.deepEqual(routesWhoseJsonLdOmitsTheirOwnDescription(drifted), [
    'route "/": description "The reworded page description." appears in no jsonLd node ' +
      '(found: ["The ORIGINAL page description.","A deliberately different one, about the framework."])',
  ]);
});

test('the description-binding guard accepts a graph whose other nodes carry their own distinct descriptions, as long as one restates the route\'s', () => {
  const inSync = [
    {
      path: '/',
      description: 'The page description.',
      jsonLd: {
        '@context': 'https://schema.org',
        '@graph': [
          { '@type': 'WebSite', description: 'The page description.' },
          { '@type': 'SoftwareSourceCode', description: 'A deliberately different one, about the framework.' },
        ],
      },
    },
    // A route with no jsonLd at all is not subject to the guard.
    { path: '/architecture', description: 'Architecture.' },
  ];

  assert.deepEqual(routesWhoseJsonLdOmitsTheirOwnDescription(inSync), []);
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
// must appear in artifactGenerationSourceFiles or globalSourceFiles in the real committed
// seo-routes.json — protects against anyone having to re-discover one of these one file at a time
// (appRoutes.ts was the first instance; this guard is broader). ----------------------------------

test('global dependency scope contract: every file traced as materially affecting every route\'s output is present in artifactGenerationSourceFiles or globalSourceFiles', () => {
  const { artifactGenerationSourceFiles, globalSourceFiles } = JSON.parse(
    readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'),
  );
  const declaredGlobally = new Set([...artifactGenerationSourceFiles, ...globalSourceFiles]);

  // Build/prerender pipeline: decides what "the rendered page" even is, for every route at once.
  const expectedArtifactGeneration = [
    'agentforge4j-web-ui/index.html',
    'agentforge4j-web-ui/src/main.tsx',
    'agentforge4j-web-ui/vite.config.ts',
    'agentforge4j-web-ui/scripts/build-seo.mjs',
    'agentforge4j-web-ui/scripts/prerender-routes.mjs',
  ];
  // Shared React render surface: the actual component tree and routing registry every page renders
  // through — including the theme machinery SiteHeader renders on every page (the toggle button
  // and its initial icon/aria state are part of every page's captured header markup).
  const expectedGlobalRender = [
    'agentforge4j-web-ui/src/App.tsx',
    'agentforge4j-web-ui/src/config/appRoutes.ts',
    'agentforge4j-web-ui/src/components/SiteHeader.tsx',
    'agentforge4j-web-ui/src/components/SiteFooter.tsx',
    'agentforge4j-web-ui/src/config/nav.ts',
    'agentforge4j-web-ui/src/components/ThemeToggle.tsx',
    'agentforge4j-web-ui/src/theme/ThemeContext.tsx',
    'agentforge4j-web-ui/src/theme/theme.ts',
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

// --- The not-found shell. copy-404.mjs copies dist/index.html verbatim (right for the body, which
// must stay the empty pre-prerender mount point), which left the head saying the home page's title,
// description, canonical and social tags on every mistyped address — and on /404.html itself, which
// is served at 200 where the HTTP status protects nothing. ---

const NOT_FOUND_CONFIG = {
  title: 'Page not found — AgentForge4j',
  description: 'This address does not match any page on agentforge4j.org.',
  robots: 'noindex, follow',
};

test('injectNotFoundHead replaces the home title/description with the not-found ones', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, NOT_FOUND_CONFIG);
  assert.match(html, /<title>Page not found — AgentForge4j<\/title>/);
  assert.match(html, /<meta name="description" content="This address does not match any page on agentforge4j\.org\." \/>/);
  assert.doesNotMatch(html, /Governed AI Workflows for Java/);
});

test('injectNotFoundHead REMOVES the canonical link rather than repointing it — a 404 must name no canonical URL at all', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, NOT_FOUND_CONFIG);
  assert.doesNotMatch(html, /<link\s+rel="canonical"/);
});

test('injectNotFoundHead removes og:url too — it makes the same claim the canonical no longer does', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, NOT_FOUND_CONFIG);
  assert.doesNotMatch(html, /property="og:url"/);
});

test('injectNotFoundHead adds the configured robots directive, since /404.html itself is served at 200', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, NOT_FOUND_CONFIG);
  assert.match(html, /<meta name="robots" content="noindex, follow" \/>/);
});

test('injectNotFoundHead rewrites the social title/description as well, so the shell does not describe itself as the home page anywhere', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, NOT_FOUND_CONFIG);
  assert.match(html, /<meta property="og:title" content="Page not found — AgentForge4j" \/>/);
  assert.match(html, /<meta name="twitter:title" content="Page not found — AgentForge4j" \/>/);
  assert.match(html, /<meta property="og:description" content="This address does not match any page on agentforge4j\.org\." \/>/);
  assert.match(html, /<meta name="twitter:description" content="This address does not match any page on agentforge4j\.org\." \/>/);
});

test('injectNotFoundHead never touches the body — the empty mount point verify-seo gates on survives', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, NOT_FOUND_CONFIG);
  assert.match(html, /<div id="root"><\/div>/);
});

test('injectNotFoundHead escapes its values into the head rather than trusting them', () => {
  const html = injectNotFoundHead(BASE_INDEX_HTML, {
    title: 'Not "found" <yet>',
    description: 'A & B',
    robots: 'noindex, follow',
  });
  assert.match(html, /<title>Not &quot;found&quot; &lt;yet&gt;<\/title>/);
  assert.match(html, /content="A &amp; B"/);
});

test('injectNotFoundHead fails loudly on template drift rather than silently leaving the home metadata in place', () => {
  const withoutTitle = BASE_INDEX_HTML.replace(/<title>[\s\S]*?<\/title>/, '');
  assert.throws(() => injectNotFoundHead(withoutTitle, NOT_FOUND_CONFIG), /expected tag not found in dist\/404\.html/);
});

test('buildSeo gives a real dist/404.html its own not-found head, and reports having done so', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    routes: { ...SAMPLE_ROUTES, notFound: NOT_FOUND_CONFIG },
  });
  // Exactly what copy-404.mjs leaves behind before this runs: a verbatim copy of the built shell.
  writeFileSync(join(distDir, '404.html'), BASE_INDEX_HTML, 'utf8');

  const result = buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot: REAL_MODULE_ROOT });
  assert.equal(result.notFoundShellWritten, true);

  const html = readFileSync(join(distDir, '404.html'), 'utf8');
  assert.match(html, /<title>Page not found — AgentForge4j<\/title>/);
  assert.doesNotMatch(html, /<link\s+rel="canonical"/);
  assert.match(html, /<meta name="robots" content="noindex, follow" \/>/);
  assert.match(html, /<div id="root"><\/div>/);
});

test('the not-found shell never receives structured data, even when a route declares some', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture({
    routes: {
      ...SAMPLE_ROUTES,
      notFound: NOT_FOUND_CONFIG,
      routes: [
        {
          path: '/',
          title: 'Home Title',
          description: 'Home description.',
          jsonLd: { '@context': 'https://schema.org', '@type': 'WebSite', name: 'AgentForge4j' },
        },
        ...SAMPLE_ROUTES.routes.slice(1),
      ],
    },
  });
  writeFileSync(join(distDir, '404.html'), BASE_INDEX_HTML, 'utf8');

  buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot: REAL_MODULE_ROOT });

  // The home shell gets it; the catch-all shell must not — that structured data asserts the URL IS
  // the site's WebSite entity, which is untrue of every address that lands on the 404.
  assert.match(readFileSync(join(distDir, 'index.html'), 'utf8'), /application\/ld\+json/);
  assert.doesNotMatch(readFileSync(join(distDir, '404.html'), 'utf8'), /application\/ld\+json/);
});

test('buildSeo leaves 404.html alone (and says so) when the config declares no notFound metadata', () => {
  const { distDir, seoRoutesPath, catalogueDataPath } = fixture();
  writeFileSync(join(distDir, '404.html'), BASE_INDEX_HTML, 'utf8');
  const result = buildSeo({ distDir, seoRoutesPath, catalogueDataPath, repoRoot: REAL_MODULE_ROOT });
  assert.equal(result.notFoundShellWritten, false);
  assert.equal(readFileSync(join(distDir, '404.html'), 'utf8'), BASE_INDEX_HTML);
});

test('the REAL committed seo-routes.json declares not-found metadata — without it every mistyped address ships the home page head', () => {
  const real = JSON.parse(readFileSync(join(REAL_MODULE_ROOT, 'src/config/seo-routes.json'), 'utf8'));
  assert.ok(real.notFound, 'expected a top-level `notFound` entry');
  assert.ok(real.notFound.title.length > 0);
  assert.ok(real.notFound.description.length > 0);
  assert.match(real.notFound.robots, /\bnoindex\b/);
  assert.notEqual(real.notFound.title, real.routes.find((route) => route.path === '/').title);
});

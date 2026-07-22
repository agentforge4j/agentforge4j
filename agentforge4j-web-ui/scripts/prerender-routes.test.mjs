// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for prerenderRoutes's pure route-inventory logic (collectRoutePaths) against
// fixture seo-routes.json/catalogue-data.json — no headless browser required. The actual browser
// rendering (real DOM content per route, matching the real built bundle) can only be proven against
// a real `vite build`, which is what package.json's own `build` script exercises via build-seo.mjs's
// CLI entry (prerenderRoutes) followed by verify-seo.mjs's real-artifact assertions.
//
// The determinism tests below DO use a real headless browser (against tiny fixture dist/ pages, not
// the real bundle) — this is the one property that cannot be proven any other way: two real builds
// from the same commit once produced byte-different output for /builder/ (nanoid's browser build
// calls crypto.getRandomValues for the default canvas's starter node id), caught by exactly this
// mechanism. These tests prove the fix generalizes (Math.random is also neutralized) and that the
// fail-closed double-capture check still catches a source of nondeterminism the RNG seeding does
// NOT cover (Date.now).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { request } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { collectRoutePaths, prerenderRoutes, resolveWithinRoot, startStaticServer } from './prerender-routes.mjs';

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

function fixtureDist(bodyScript) {
  const root = mkdtempSync(join(tmpdir(), 'prerender-determinism-'));
  const distDir = join(root, 'dist');
  mkdirSync(distDir, { recursive: true });
  writeFileSync(
    join(distDir, 'index.html'),
    `<!doctype html><html><head></head><body><div id="root"></div>` +
      `<script>${bodyScript}</script></body></html>`,
    'utf8',
  );
  return distDir;
}

function fixture({ routes, workflows = [] }) {
  const root = mkdtempSync(join(tmpdir(), 'prerender-routes-'));
  const seoRoutesPath = join(root, 'seo-routes.json');
  writeFileSync(seoRoutesPath, JSON.stringify({ siteUrl: 'https://agentforge4j.org', routes }), 'utf8');
  const catalogueDataPath = join(root, 'catalogue-data.json');
  writeFileSync(catalogueDataPath, JSON.stringify({ workflows }), 'utf8');
  return { seoRoutesPath, catalogueDataPath };
}

test('collects every static route path from seo-routes.json, in order', () => {
  const { seoRoutesPath, catalogueDataPath } = fixture({
    routes: [{ path: '/' }, { path: '/api' }, { path: '/builder' }],
  });
  assert.deepEqual(collectRoutePaths({ seoRoutesPath, catalogueDataPath }), ['/', '/api', '/builder']);
});

test('appends one /catalogue/<id> path per real shipped workflow', () => {
  const { seoRoutesPath, catalogueDataPath } = fixture({
    routes: [{ path: '/' }, { path: '/catalogue' }],
    workflows: [{ id: 'agent-creator' }, { id: 'workflow-execution-estimator' }],
  });
  assert.deepEqual(collectRoutePaths({ seoRoutesPath, catalogueDataPath }), [
    '/',
    '/catalogue',
    '/catalogue/agent-creator',
    '/catalogue/workflow-execution-estimator',
  ]);
});

test('tolerates a missing catalogue-data.json (pre-catalogue:build state) by contributing zero workflow routes', () => {
  const root = mkdtempSync(join(tmpdir(), 'prerender-routes-'));
  const seoRoutesPath = join(root, 'seo-routes.json');
  writeFileSync(seoRoutesPath, JSON.stringify({ siteUrl: 'https://agentforge4j.org', routes: [{ path: '/' }] }), 'utf8');
  const catalogueDataPath = join(root, 'does-not-exist.json');
  assert.deepEqual(collectRoutePaths({ seoRoutesPath, catalogueDataPath }), ['/']);
});

test('captures identical markup across two navigations for genuinely static content', async () => {
  const distDir = fixtureDist('document.getElementById("root").innerHTML = "<h1>Static Title</h1>";');
  const snapshots = await prerenderRoutes({ distDir, routePaths: ['/'] });
  assert.equal(snapshots['/'], '<h1>Static Title</h1>');
});

test('Math.random-derived content is neutralized to deterministic output by the seeded-RNG init script', async () => {
  const distDir = fixtureDist(
    'document.getElementById("root").innerHTML = "<h1>Roll " + Math.floor(Math.random() * 1e9) + "</h1>";',
  );
  const snapshots = await prerenderRoutes({ distDir, routePaths: ['/'] });
  // Two independent prerenderRoutes() runs (fresh browser each time) must agree — proving the seed
  // is fixed, not merely "stable within one run by luck".
  const secondRun = await prerenderRoutes({ distDir, routePaths: ['/'] });
  assert.equal(snapshots['/'], secondRun['/']);
});

test('crypto.getRandomValues-derived content is neutralized too (the real defect this pass caught: nanoid uses it, not Math.random)', async () => {
  const distDir = fixtureDist(
    'const b = new Uint8Array(4); crypto.getRandomValues(b); ' +
      'document.getElementById("root").innerHTML = "<h1>Bytes " + Array.from(b).join(",") + "</h1>";',
  );
  const snapshots = await prerenderRoutes({ distDir, routePaths: ['/'] });
  const secondRun = await prerenderRoutes({ distDir, routePaths: ['/'] });
  assert.equal(snapshots['/'], secondRun['/']);
});

test('fails closed (does not silently ship) when a route is genuinely nondeterministic in a way the RNG seeding cannot cover', async () => {
  const distDir = fixtureDist('document.getElementById("root").innerHTML = "<h1>At " + Date.now() + "</h1>";');
  await assert.rejects(
    () => prerenderRoutes({ distDir, routePaths: ['/'] }),
    /produced different markup across two consecutive captures/,
  );
});

// --- resolveWithinRoot: path-containment guard for the static server's own request handling ---
// (a bare `candidate.startsWith(distDir)` check would incorrectly pass a sibling directory sharing
// the same string prefix, e.g. `dist-evil` next to `dist` — this is what closes that gap).

test('resolveWithinRoot: a normal relative path resolves inside the root', () => {
  const root = join(tmpdir(), 'containment-root');
  assert.equal(resolveWithinRoot(root, '/assets/app.js'), join(root, 'assets', 'app.js'));
  assert.equal(resolveWithinRoot(root, '/'), root);
});

test('resolveWithinRoot: a `../` traversal segment is rejected (returns null), however deep', () => {
  const root = join(tmpdir(), 'containment-root');
  assert.equal(resolveWithinRoot(root, '/../secret.txt'), null);
  assert.equal(resolveWithinRoot(root, '/../../../../etc/passwd'), null);
});

test('resolveWithinRoot: an already-decoded traversal segment is rejected identically (the server decodeURIComponents the URL before calling this)', () => {
  const root = join(tmpdir(), 'containment-root');
  // %2e%2e%2f decodes to "../" before this function ever sees it — same input, same rejection.
  assert.equal(resolveWithinRoot(root, decodeURIComponent('/%2e%2e%2fsecret.txt')), null);
});

test('resolveWithinRoot: a sibling directory sharing the same string prefix is rejected, not treated as a child', () => {
  const root = join(tmpdir(), 'dist');
  // "../dist-evil/secret.txt" resolves to a real sibling of `root` that a naive
  // `candidate.startsWith(root)` bare-prefix check would incorrectly accept.
  assert.equal(resolveWithinRoot(root, '/../dist-evil/secret.txt'), null);
});

// --- startStaticServer: the same containment guard, exercised through a real HTTP round-trip ---

function fixtureDistWithSibling() {
  const root = mkdtempSync(join(tmpdir(), 'prerender-containment-'));
  const distDir = join(root, 'dist');
  mkdirSync(join(distDir, 'assets'), { recursive: true });
  writeFileSync(join(distDir, 'index.html'), '<!doctype html><html><body><div id="root">shell</div></body></html>', 'utf8');
  writeFileSync(join(distDir, 'assets', 'app.js'), 'console.log("real asset");', 'utf8');
  const siblingDir = join(root, 'dist-evil');
  mkdirSync(siblingDir, { recursive: true });
  writeFileSync(join(siblingDir, 'secret.txt'), 'THIS MUST NEVER BE SERVED', 'utf8');
  return { distDir, siblingDir };
}

test('startStaticServer serves a real file inside the root normally', async () => {
  const { distDir } = fixtureDistWithSibling();
  const server = await startStaticServer(distDir);
  try {
    const { port } = server.address();
    const { status, body } = await rawGet(port, '/assets/app.js');
    assert.equal(status, 200);
    assert.equal(body, 'console.log("real asset");');
  } finally {
    await new Promise((r) => server.close(r));
  }
});

test('startStaticServer never leaks a file reached via `../` traversal — falls back to the safe SPA shell instead', async () => {
  const { distDir, siblingDir } = fixtureDistWithSibling();
  // Sanity check on the fixture itself, not the server: proves the sibling file genuinely exists
  // on disk (so this test is proving containment, not merely that the path didn't exist).
  assert.ok(existsSync(join(siblingDir, 'secret.txt')));
  const server = await startStaticServer(distDir);
  try {
    const { port } = server.address();
    const { body } = await rawGet(port, '/../dist-evil/secret.txt');
    assert.ok(!body.includes('THIS MUST NEVER BE SERVED'), 'the traversal-reached sibling file must never be served');
    assert.ok(body.includes('shell'), 'must fall back to the real SPA shell (index.html), not error or leak content');
  } finally {
    await new Promise((r) => server.close(r));
  }
});

test('startStaticServer never leaks a file reached via an encoded `../` traversal form either', async () => {
  const { distDir, siblingDir } = fixtureDistWithSibling();
  assert.ok(existsSync(join(siblingDir, 'secret.txt')));
  const server = await startStaticServer(distDir);
  try {
    const { port } = server.address();
    // %2e%2e is "%2e%2e" -> ".." URL-encoded — the server decodeURIComponents the raw request path
    // itself, so this must be rejected exactly like the unencoded form above.
    const { body } = await rawGet(port, '/%2e%2e/dist-evil/secret.txt');
    assert.ok(!body.includes('THIS MUST NEVER BE SERVED'), 'the traversal-reached sibling file must never be served');
    assert.ok(body.includes('shell'), 'must fall back to the real SPA shell (index.html), not error or leak content');
  } finally {
    await new Promise((r) => server.close(r));
  }
});

// --- Malformed percent-encoding: decodeURIComponent throws URIError on a truncated/invalid escape
// — uncaught, that would crash the whole process, taking every other in-flight prerender capture
// down with it. ---

test('startStaticServer returns a controlled 400 for malformed percent-encoding, and stays alive to serve a valid request afterward', async () => {
  const { distDir } = fixtureDistWithSibling();
  const server = await startStaticServer(distDir);
  try {
    const { port } = server.address();
    const malformed = await rawGet(port, '/%E0%A4%A');
    assert.equal(malformed.status, 400);

    // The server process must not have crashed: a real, valid request right after must still work.
    const valid = await rawGet(port, '/assets/app.js');
    assert.equal(valid.status, 200);
    assert.equal(valid.body, 'console.log("real asset");');
  } finally {
    await new Promise((r) => server.close(r));
  }
});

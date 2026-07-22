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
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { collectRoutePaths, prerenderRoutes } from './prerender-routes.mjs';

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

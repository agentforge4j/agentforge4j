// SPDX-License-Identifier: Apache-2.0
//
// Verifies the site's CURRENT, TEMPORARY Day 1 hosting mechanism only: an un-assembled SPA build
// deployed to plain GitHub Pages, where `dist/404.html` (byte-identical to `dist/index.html`, per
// `scripts/copy-404.mjs`) is served with a real HTTP 404 status for any path with no matching
// on-disk file, and that page then boots the SPA client-side to render the correct route.
//
// HTTP 404 for a known public/catalogue route is NOT the required final production contract —
// it is what an un-assembled SPA build unavoidably does on plain static hosting, and it is only
// acceptable here because Day 1.5 replaces it. See `agentforge4j-ui-e2e/README.md` ("Day 1.5
// hosting contract") for the release-blocking requirement the Assembler must satisfy before
// launch: known public routes and known catalogue detail routes must return HTTP 200; only
// genuinely unknown routes may return HTTP 404. Implementing that Assembler is out of scope here.
//
// Deliberately does NOT use the shared `webServer` (`vite preview`) — Vite's own dev/preview
// servers have SPA-fallback middleware baked in that would return 200 and mask whether the
// 404.html mechanism itself actually works. This spec runs a minimal static file server against
// the already-built `dist/` (built once for the whole run by the shared `webServer` command) that
// returns a real, GitHub-Pages-equivalent 404.

import { createServer, type Server } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { expect, test } from '@playwright/test';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { CONTENT_TYPES } from '../../support/content-types.mjs';

const DIST_DIR = join(import.meta.dirname, '..', '..', '..', 'agentforge4j-web-ui', 'dist');

let server: Server;
let baseUrl: string;

test.beforeAll(async () => {
  server = createServer(async (req, res) => {
    const requestedPath = join(DIST_DIR, decodeURIComponent((req.url ?? '/').split('?')[0]));
    try {
      const body = await readFile(requestedPath);
      res.writeHead(200, { 'Content-Type': CONTENT_TYPES[extname(requestedPath)] ?? 'application/octet-stream' });
      res.end(body);
    } catch {
      // GitHub Pages' real behaviour for any path with no matching file: HTTP 404, served with
      // whatever the repository's own 404.html contains.
      const notFound = await readFile(join(DIST_DIR, '404.html'));
      res.writeHead(404, { 'Content-Type': 'text/html' });
      res.end(notFound);
    }
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (address === null || typeof address === 'string') {
    throw new Error('hosting.spec: server did not bind to a TCP port');
  }
  baseUrl = `http://127.0.0.1:${address.port}`;
});

test.afterAll(async () => {
  await new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
});

test.describe('GitHub-Pages-style SPA-fallback hosting (Day 1 — temporary, superseded by the Day 1.5 Assembler)', () => {
  test('a real on-disk asset returns 200 (sanity check the dumb server serves real files)', async ({
    request,
  }) => {
    const response = await request.get(`${baseUrl}/index.html`);
    expect(response.status()).toBe(200);
  });

  // Day 1 (current, temporary): a known public route with no matching on-disk file gets the
  // 404.html SPA-fallback and still renders correctly client-side. This 404 status is NOT the
  // required final answer — the Day 1.5 hosting contract (see this file's header comment and the
  // README) requires known public routes to return HTTP 200.
  test('a nested route requested directly currently gets the SPA-fallback 404, and still boots the correct page', async ({
    page,
  }) => {
    const response = await page.goto(`${baseUrl}/architecture`);
    expect(response?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1, name: 'Architecture' })).toBeVisible();
  });

  // Day 1 (current, temporary): same SPA-fallback 404 on a second direct load (e.g. a browser
  // refresh) of a known catalogue route. Also not the required final answer — see above.
  test('refreshing a nested route (a second direct load) also currently gets the SPA-fallback 404, then still renders correctly', async ({
    page,
  }) => {
    await page.goto(`${baseUrl}/catalogue`);
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow catalogue' })).toBeVisible();
    const response = await page.reload();
    expect(response?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow catalogue' })).toBeVisible();
  });
});

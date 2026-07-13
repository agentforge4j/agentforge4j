// SPDX-License-Identifier: Apache-2.0
//
// Verifies the site's actual intended production hosting mechanism: GitHub Pages serves
// `dist/404.html` (byte-identical to `dist/index.html`, per `scripts/copy-404.mjs`) with a real
// HTTP 404 status for any unmatched path, and that page boots the SPA, which then renders the
// correct route client-side. Deliberately does NOT use the shared `webServer` (`vite preview`) —
// Vite's own dev/preview servers have SPA-fallback middleware baked in that would return 200 and
// mask whether the 404.html mechanism itself actually works. This spec runs a minimal static file
// server against the already-built `dist/` (built once for the whole run by the shared
// `webServer` command) that returns a real, GitHub-Pages-equivalent 404.

import { createServer, type Server } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { expect, test } from '@playwright/test';

const DIST_DIR = join(import.meta.dirname, '..', '..', '..', 'agentforge4j-web-ui', 'dist');

const CONTENT_TYPES: Record<string, string> = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
};

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

test.describe('GitHub-Pages-style static hosting', () => {
  test('a real on-disk asset returns 200 (sanity check the dumb server serves real files)', async ({
    request,
  }) => {
    const response = await request.get(`${baseUrl}/index.html`);
    expect(response.status()).toBe(200);
  });

  test('a nested route requested directly returns a real HTTP 404, and still boots the correct page', async ({
    page,
  }) => {
    const response = await page.goto(`${baseUrl}/architecture`);
    expect(response?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1, name: 'Architecture' })).toBeVisible();
  });

  test('refreshing a nested route (a second direct load) still renders correctly', async ({ page }) => {
    await page.goto(`${baseUrl}/catalogue`);
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow catalogue' })).toBeVisible();
    const response = await page.reload();
    expect(response?.status()).toBe(404);
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow catalogue' })).toBeVisible();
  });
});
